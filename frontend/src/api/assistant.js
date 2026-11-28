import { getToken } from '@/utils/auth'

/**
 * 助教问答：SSE 流式客户端。
 *
 * 为什么不用 axios：axios 的浏览器实现基于 XHR，`responseType` 里没有
 * 增量读取流的能力（`stream` 只在 Node 端可用），拿不到逐段到达的文本。
 * 这里用 fetch + ReadableStream 自己读，代价是要手动处理鉴权头与错误。
 *
 * 事件顺序是固定的：citations → delta* → done。
 *
 * 两条链路由 `scope` 决定，区别全在后端：
 *   - `tutor` 学习答疑：纯生成式，不检索、不引用、不拒答；
 *   - `job` 岗位咨询：RAG，只依据系统里在架的职位记录，查不到就拒答。
 * 前端不需要为两种模式写两套请求，事件协议是完全一致的。
 *
 * @param {Object}   opts
 * @param {string}   opts.question   当前问题
 * @param {string}   opts.scope      'tutor' 学习答疑 / 'job' 岗位咨询；缺省按 tutor
 * @param {string}   opts.context    场景提示（课程名、职位名这类背景），后端限 60 字
 * @param {Array}    opts.history    历史对话 [{role, content}]，最多 8 条（4 轮）
 * @param {AbortSignal} opts.signal  中断信号
 * @param {Function} opts.onCitations 收到引用列表时回调 ({citations, rejected, hitCount, topScore})
 * @param {Function} opts.onDelta     收到一段答案文本时回调 (text)
 * @param {Function} opts.onDone      正常结束时回调 ({elapsedMs, model, hitCount, rejected})
 */
export async function streamChat({ question, scope = 'tutor', context = '', history = [], signal, onCitations, onDelta, onDone }) {
  const token = getToken()

  const payload = { question, scope, history }
  // context 为空就不带这个字段：后端把空串也当作「没有背景」，
  // 但少一个字段总比多一个语义不明的字段好。
  if (context) payload.context = context

  const res = await fetch('/api/assistant/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload),
    signal
  })

  // 参数校验、未登录、检索异常都会走到这里：后端统一返回 JSON，不是事件流
  const contentType = res.headers.get('content-type') || ''
  if (!res.ok || !contentType.includes('text/event-stream')) {
    const body = await res.json().catch(() => null)
    throw new Error(body?.message || `请求失败（${res.status}）`)
  }
  if (!res.body) throw new Error('当前浏览器不支持流式响应')

  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  // 按空行切事件；最后一段可能不完整，留在 buffer 里等下一块
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let boundary
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const raw = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        dispatch(raw, { onCitations, onDelta, onDone })
      }
    }
  } catch (e) {
    // 收到 error 事件或读到一半断流：释放连接再往上抛，避免 reader 一直锁着
    reader.cancel().catch(() => {})
    throw e
  }
}

/**
 * 解析单个 SSE 事件块并分派。
 *
 * @param {string} raw 原始事件块（不含结尾空行）
 * @param {Object} handlers 回调集合
 */
function dispatch(raw, handlers) {
  let event = 'message'
  let data = ''
  for (const line of raw.split('\n')) {
    const text = line.endsWith('\r') ? line.slice(0, -1) : line
    if (text.startsWith('event:')) {
      event = text.slice(6).trim()
    } else if (text.startsWith('data:')) {
      // 协议允许多行 data，按规范用换行拼接
      data += text.slice(5)
    }
  }
  if (!data) return

  let payload
  try {
    payload = JSON.parse(data)
  } catch {
    return
  }

  if (event === 'citations') handlers.onCitations?.(payload)
  else if (event === 'delta') handlers.onDelta?.(payload.text || '')
  else if (event === 'done') handlers.onDone?.(payload)
  else if (event === 'error') throw new Error(payload.message || '回答生成失败')
}
