<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/store/auth'
import { streamChat } from '@/api/assistant'

const props = defineProps({
  // 问答类型：tutor 学习答疑（纯生成）/ job 岗位咨询（带引用，可能拒答）
  scope: { type: String, default: 'tutor' },
  // 场景提示：课程名、职位名这类背景；后端限 60 字，只影响 tutor 怎么讲
  context: { type: String, default: '' },
  // 紧凑模式：悬浮面板里用，字号与留白都收一档
  compact: { type: Boolean, default: false }
})

const auth = useAuthStore()

const messages = ref([])
const input = ref('')
const sending = ref(false)
const scroller = ref(null)
let controller = null

// job 链路才走检索与拒答；tutor 是纯生成，天然不会拒答
const isJob = computed(() => props.scope === 'job')

const scopeLabel = computed(() => (isJob.value ? '岗位咨询' : '学习答疑'))

const thinkingText = computed(() => (isJob.value ? '正在检索岗位记录' : '正在组织回答'))

const introText = computed(() => (isJob.value
  ? '只按系统里在架的职位记录回答，记录里没有的会直接说没有。'
  : '想到什么问什么。概念、写法、面试怎么答都行，不用先在课程资料里找到出处。'))

const suggestions = computed(() => (isJob.value
  ? ['北京有哪些 Java 后端岗位？', '有没有实习岗位？', '前端岗位有哪些，要求是什么？']
  : ['Java 的多态怎么理解？', '零基础该先学什么？', '项目经历在简历里怎么写？']))

// 后端对 context 有 60 字上限，超了会直接判校验失败；前端先截一刀，别把请求打成 400
const safeContext = computed(() => (props.context || '').trim().slice(0, 60))

function scrollToBottom() {
  nextTick(() => {
    const el = scroller.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/**
 * 取最近 10 轮作为上下文。注意要排除当前这一问：
 * 后端拿 history 做指代消解，若把当前问句也放进去会自己指代自己。
 */
const HISTORY_MAX_TURNS = 20
/** 单条发回后端的字符上限；只截断副本，界面上的消息保持完整 */
const HISTORY_MAX_ITEM = 2000

function buildHistory() {
  return messages.value
    .filter(m => m.role === 'user' || (m.status === 'done'))
    .map(m => ({
      role: m.role,
      content: m.text.length > HISTORY_MAX_ITEM ? m.text.slice(0, HISTORY_MAX_ITEM) + '…' : m.text
    }))
    .filter(m => m.content && m.content.trim())
    .slice(-HISTORY_MAX_TURNS)
}

/**
 * 会话落 sessionStorage：用户切到别的页面再回来，追问还能接上。
 *
 * 为什么是 sessionStorage 而不是 localStorage：一次学习会话结束后不该在
 * 共享电脑上留下对话记录；关掉标签页就没了，符合直觉。
 * key 带上 scope 与 context，课程页的答疑与岗位页的咨询不会互相串。
 *
 * 只在「用户发言 / 回答结束 / 清空」三个时机写盘，不在每个 token 上写——
 * 流式输出每来一个字就 JSON.stringify 整个会话，会把主线程拖垮。
 */
function storageKey() {
  const ctx = (props.context || '').trim()
  return `ferry_chat:${props.scope}:${ctx || '-'}`
}

function persist() {
  try {
    if (!messages.value.length) {
      sessionStorage.removeItem(storageKey())
      return
    }
    sessionStorage.setItem(storageKey(), JSON.stringify(messages.value))
  } catch (e) {
    // 隐私模式或超配额：存不下就算了，不能因此影响对话本身
  }
}

function restore() {
  try {
    const raw = sessionStorage.getItem(storageKey())
    if (!raw) return
    const list = JSON.parse(raw)
    if (!Array.isArray(list)) return
    messages.value = list
      .filter(m => m && m.role && typeof m.text === 'string')
      .map(m => ({
        role: m.role,
        text: m.text,
        citations: Array.isArray(m.citations) ? m.citations : [],
        // 恢复出来的消息一律视为已完成：绝不能让上一轮的气泡继续转圈
        status: 'done',
        rejected: !!m.rejected,
        topScore: m.topScore || 0,
        error: '',
        firstAt: 0,
        elapsed: m.elapsed || 0,
        openIndex: -1
      }))
  } catch (e) {
    // 坏数据当作没有，不要让一条脏缓存把整个页面搞白
  }
}

function clearAll() {
  messages.value = []
  persist()
}

onMounted(() => {
  restore()
  scrollToBottom()
})

/**
 * 岗位模式的拒答。citations 事件一定先于第一条 delta 到达，
 * 所以这里能在任何正文渲染之前就判定出来，不会出现「先闪一下答案再变成拒答」。
 */
function isJobReject(m) {
  return isJob.value && !!m.rejected
}

async function send(text) {
  const question = (text ?? input.value).trim()
  if (!question || sending.value) return
  // 接口要登录态，公开页上的浮标没登录时直接拦掉，别让用户等一个 401
  if (!auth.isLogin) return
  const history = buildHistory()
  input.value = ''
  messages.value.push({ role: 'user', text: question })
  persist()

  const startedAt = performance.now()
  // 必须是 reactive 而不是 ref({...}).value：后者拿到的是原始对象，
  // 推进数组后再改它绕过了 Proxy，流式追加的文本不会触发视图更新。
  const msg = reactive({
    role: 'assistant', text: '', citations: [], status: 'thinking',
    rejected: false, topScore: 0, error: '', firstAt: 0, elapsed: 0, openIndex: -1
  })
  messages.value.push(msg)
  sending.value = true
  scrollToBottom()

  controller = new AbortController()
  try {
    await streamChat({
      question,
      scope: props.scope,
      context: safeContext.value,
      history,
      signal: controller.signal,
      onCitations: payload => {
        msg.citations = payload.citations || []
        msg.rejected = !!payload.rejected
        msg.topScore = payload.topScore || 0
        scrollToBottom()
      },
      onDelta: delta => {
        if (!msg.firstAt) msg.firstAt = performance.now() - startedAt
        msg.status = 'streaming'
        msg.text += delta
        scrollToBottom()
      },
      onDone: payload => {
        msg.status = 'done'
        msg.elapsed = payload.elapsedMs || 0
      }
    })
    // 正常收流但没收到 done（老版本后端/网络截断）也要收尾，避免转圈不停
    if (msg.status === 'thinking' || msg.status === 'streaming') msg.status = 'done'
  } catch (e) {
    if (e.name === 'AbortError') {
      // 用户点了停止：一个字都没吐出来时给个明确交代，别留空气泡
      if (!msg.text) msg.text = '（已停止）'
      msg.status = 'done'
    } else {
      msg.status = 'error'
      msg.error = e.message || '请求失败'
    }
  } finally {
    sending.value = false
    controller = null
    scrollToBottom()
    // 一轮结束后落盘：此时内容才稳定，流式过程中写盘是浪费
    persist()
  }
}

function onKeydown(e) {
  // Enter 发送，Shift+Enter 换行
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

function stop() {
  controller?.abort()
}

/**
 * 把答案渲染成「文本块 / 代码块 / 标题块」序列。
 *
 * 为什么不直接 {{ text }}：模型输出里既有 ``` 围栏代码块，也有 `行内代码`，
 * 原样展示会看到一堆反引号。这里做一个极小的渲染器——只处理围栏代码、
 * 行内代码、引用角标与 Markdown 标题，其余一律按纯文本转义输出，
 * 不引入 markdown 库，也不给模型输出任何执行 HTML 的机会。
 */
function blocks(text, citeCount, streaming) {
  if (!text) return streaming ? [{ type: 'text', segs: [caretSeg()] }] : []
  const out = []
  const fence = /```[a-zA-Z]*\n?([\s\S]*?)```/g
  let last = 0
  let match
  while ((match = fence.exec(text)) !== null) {
    if (match.index > last) out.push(...splitHeadings(text.slice(last, match.index), citeCount))
    out.push({ type: 'code', text: match[1].replace(/\n$/, '') })
    last = fence.lastIndex
  }
  if (last < text.length) out.push(...splitHeadings(text.slice(last), citeCount))

  if (streaming) {
    // 光标要跟在最后一个字后面，单独放会让它掉到下一行
    const tail = out[out.length - 1]
    if (tail && (tail.type === 'text' || tail.type === 'heading')) tail.segs.push(caretSeg())
    else out.push({ type: 'text', segs: [caretSeg()] })
  }
  return out
}

/**
 * 把一段正文拆成「标题 / 段落」交替的块。
 *
 * 答疑模式是生成式的，模型会写 `### 解决方法` 这种 Markdown 标题，
 * 原样显示就是一串井号。整行以 `#` 开头（井号后要有空格）才算标题，
 * 判定与 CommonMark 一致。项目里没装 markdown 库，也不值得为几个标题引一个，
 * 所以在现有渲染器里补这一条规则就够了。
 *
 * @param {string} text 正文片段（已剥离代码块）
 * @param {number} citeCount 引用条数，角标编号不能超过它
 * @returns {Array} 块序列，type 为 'text' 或 'heading'
 */
function splitHeadings(text, citeCount) {
  const out = []
  const lines = []

  const flush = (nextIsHeading) => {
    if (!lines.length) return
    let chunk = lines.join('\n')
    lines.length = 0
    // 纯空白的段落不渲染：标题之间夹的空行没必要单独占一个 p
    if (!chunk.trim()) return
    // 标题自己带上下间距，紧跟其后的段落就不必再留一行空行
    if (out.length && out[out.length - 1].type === 'heading') chunk = chunk.replace(/^\n+/, '')
    // 同理，紧挨在标题前面的段落也不用把空行带出来
    if (nextIsHeading) chunk = chunk.replace(/\n+$/, '')
    out.push({ type: 'text', segs: splitCites(chunk, citeCount) })
  }

  for (const line of text.split('\n')) {
    const m = line.match(/^(#{1,6})[ \t]+(.*)$/)
    if (!m) {
      lines.push(line)
      continue
    }
    flush(true)
    out.push({
      type: 'heading',
      // 气泡里不放 h1/h2：标题压到 h3/h4，免得一个「## 概述」把整段回答顶下去
      level: m[1].length <= 3 ? 3 : 4,
      // 标题里也可能夹着 [1]，角标照样得能点
      segs: splitCites(m[2].trim(), citeCount)
    })
  }
  flush(false)
  return out
}

function caretSeg() {
  return { html: '<span class="caret"></span>' }
}

function splitCites(text, citeCount) {
  const segs = []
  const re = /\[(\d{1,2})]/g
  let last = 0
  let match
  while ((match = re.exec(text)) !== null) {
    if (match.index > last) segs.push({ html: inline(text.slice(last, match.index)) })
    const n = Number(match[1])
    // 超出引用条数的编号按纯文本渲染：那多半是模型自己编的
    if (n >= 1 && n <= citeCount) segs.push({ cite: n })
    else segs.push({ html: escapeHtml(match[0]) })
    last = match.index + match[0].length
  }
  if (last < text.length) segs.push({ html: inline(text.slice(last)) })
  return segs
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function inline(s) {
  return escapeHtml(s).replace(/`([^`\n]+)`/g, '<code>$1</code>')
}

function toggleCite(msg, index) {
  msg.openIndex = msg.openIndex === index ? -1 : index
}

onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="chat" :class="{ compact }">
    <div class="scope">
      <div class="scope-left">
        <span class="tag" :class="isJob ? 'tag--violet' : 'tag--slate'">{{ scopeLabel }}</span>
        <!-- 背景单独一行显示：课程名可能很长，塞进标签会把整行撑破 -->
        <span v-if="safeContext" class="scope-ctx" :title="safeContext">{{ safeContext }}</span>
      </div>
      <button v-if="messages.length" class="btn btn-ghost btn-sm" @click="clearAll">清空</button>
    </div>

    <div ref="scroller" class="stream">
      <div v-if="!messages.length" class="intro">
        <p class="intro-title font-display">问渡口</p>
        <p class="intro-sub">{{ introText }}</p>
        <div v-if="auth.isLogin" class="suggests">
          <button v-for="s in suggestions" :key="s" class="chip" @click="send(s)">{{ s }}</button>
        </div>
      </div>

      <div v-for="(m, mi) in messages" :key="mi" class="row" :class="m.role">
        <div class="bubble" :class="{ error: m.status === 'error' }">
          <template v-if="m.role === 'user'">
            <p class="plain">{{ m.text }}</p>
          </template>

          <template v-else>
            <p v-if="m.status === 'thinking' && !isJobReject(m)" class="thinking">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              {{ thinkingText }}
            </p>

            <!-- 岗位拒答：只给一句明确交代，不铺引用区，也不展示后端那句固定回答 -->
            <p v-else-if="isJobReject(m)" class="reject">
              系统里暂时没有符合条件的岗位。换个城市、岗位名或薪资范围再问一次试试。
            </p>

            <div v-else class="answer">
              <template v-for="(block, bi) in blocks(m.text, m.citations.length, m.status === 'streaming')" :key="bi">
                <pre v-if="block.type === 'code'" class="code"><code>{{ block.text }}</code></pre>
                <!-- 标题与正文共用一套角标渲染：标题里也可能夹着 [1] -->
                <component
                  v-else
                  :is="block.type === 'heading' ? 'h' + block.level : 'p'"
                  :class="block.type === 'heading' ? 'heading' : 'para'"
                >
                  <template v-for="(seg, si) in block.segs" :key="si">
                    <button
                      v-if="seg.cite"
                      class="cite-ref"
                      :title="m.citations[seg.cite - 1]?.title"
                      @click="toggleCite(m, seg.cite - 1)"
                    >{{ seg.cite }}</button>
                    <span v-else v-html="seg.html"></span>
                  </template>
                </component>
              </template>
            </div>

            <p v-if="m.status === 'error'" class="err">{{ m.error }}</p>

            <p v-if="m.rejected && m.status === 'done' && !isJob" class="hint">
              资料里最相关的一条只有 {{ Math.round((m.topScore || 0) * 100) }}% 相关，所以没有作答。
              可以换个问法，或确认相关资料里是否已经收录了这个知识点。
            </p>

            <div v-if="m.citations.length && !isJobReject(m)" class="cites">
              <button
                v-for="(c, ci) in m.citations"
                :key="c.chunkId"
                class="cite-chip"
                :class="{ active: m.openIndex === ci }"
                @click="toggleCite(m, ci)"
              >
                <span class="cite-no">{{ ci + 1 }}</span>
                <span class="cite-title">{{ c.title }}</span>
              </button>
            </div>

            <div v-if="m.openIndex >= 0 && m.citations[m.openIndex]" class="source">
              <div class="source-head">
                <span class="source-title">{{ m.citations[m.openIndex].title }}</span>
                <span class="source-meta">
                  第 {{ (m.citations[m.openIndex].chunkNo ?? 0) + 1 }} 块 ·
                  相关度 {{ Math.round((m.citations[m.openIndex].score || 0) * 100) }}%
                </span>
              </div>
              <p class="source-body">{{ m.citations[m.openIndex].content }}</p>
              <!-- 岗位来源的引用能直接跳到岗位详情；课程来源没有 jobId，就不显示这个入口 -->
              <router-link
                v-if="m.citations[m.openIndex].jobId"
                class="job-link"
                :to="`/job/${m.citations[m.openIndex].jobId}`"
              >查看岗位详情 →</router-link>
            </div>

            <p v-if="m.status === 'done' && m.elapsed" class="stamp">
              用时 {{ (m.elapsed / 1000).toFixed(1) }}s<template v-if="m.firstAt"> · 首字 {{ (m.firstAt / 1000).toFixed(1) }}s</template>
            </p>
          </template>
        </div>
      </div>
    </div>

    <div class="composer">
      <p v-if="!auth.isLogin" class="gate">
        登录之后才能提问。<router-link to="/login" class="gate-link">去登录</router-link>
      </p>
      <textarea
        v-model="input"
        class="textarea"
        :rows="compact ? 2 : 2"
        :disabled="!auth.isLogin"
        :placeholder="auth.isLogin ? '问点什么…… Enter 发送，Shift + Enter 换行' : '登录后可以提问'"
        @keydown="onKeydown"
      ></textarea>
      <div class="actions">
        <span class="counter" :class="{ 'counter--long': input.length > 5000 }">
          {{ input.length }} 字{{ input.length > 5000 ? ' · 长文本，回答可能稍慢' : '' }}
        </span>
        <button v-if="sending" class="btn btn-ghost btn-sm" @click="stop">停止</button>
        <button class="btn btn-primary btn-sm" :disabled="sending || !input.trim() || !auth.isLogin" @click="send()">发送</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat { display: flex; flex-direction: column; height: 100%; min-height: 0; }

.scope { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 0 0 10px; }
.scope-left { display: flex; align-items: center; gap: 8px; min-width: 0; }
.scope-ctx { color: var(--text-faint); font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 20em; }

.stream { flex: 1; overflow-y: auto; padding: 4px 2px 10px; min-height: 0; }

.intro { padding: 26px 4px 10px; }
.intro-title { font-size: 1.6rem; margin-bottom: 6px; }
.intro-sub { color: var(--text-dim); font-size: 14px; max-width: 52ch; }
.suggests { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 16px; }

.row { display: flex; margin-bottom: 14px; }
.row.user { justify-content: flex-end; }

.bubble {
  max-width: 86%; padding: 12px 15px; border-radius: var(--radius-card);
  background: var(--moss); border: 1px solid var(--fern); font-size: 14px;
  word-break: break-word;
}
.row.user .bubble { background: rgba(139, 92, 246, .10); border-color: rgba(139, 92, 246, .22); }
.bubble.error { border-color: rgba(220, 38, 38, .35); }
.plain { white-space: pre-wrap; }

.answer { line-height: 1.75; }
.para { white-space: pre-wrap; }
.para + .para { margin-top: 8px; }
.para code { padding: 1px 5px; border-radius: 3px; background: rgba(139, 92, 246, .09); font-family: ui-monospace, Consolas, monospace; font-size: 12.5px; }
/* 标题：层级被压到 h3/h4，靠留白和字重区分，不另起一套颜色 */
.heading { margin: 16px 0 4px; line-height: 1.5; font-weight: 600; }
.heading:first-child { margin-top: 0; }
h3.heading { font-size: 1.05rem; }
h4.heading { font-size: .95rem; }
.code {
  margin: 10px 0; padding: 10px 12px; overflow-x: auto;
  background: #f3f8f4; border: 1px solid var(--fern); border-radius: var(--radius);
  font-size: 12.5px; line-height: 1.6;
}
.code code { font-family: ui-monospace, Consolas, monospace; white-space: pre; }
.err { color: #dc2626; font-size: 13px; margin-top: 6px; }
.hint { color: var(--text-faint); font-size: 12.5px; margin-top: 8px; }
.reject { color: var(--text-dim); font-size: 14px; line-height: 1.7; }
.stamp { color: var(--text-faint); font-size: 11.5px; margin-top: 10px; }

.cite-ref {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 17px; height: 17px; padding: 0 4px; margin: 0 2px;
  border: none; border-radius: 4px; cursor: pointer;
  background: rgba(139, 92, 246, .14); color: var(--firefly-soft);
  font-size: 11px; font-weight: 600; line-height: 1; vertical-align: super;
}
.cite-ref:hover { background: var(--firefly); color: #fff; }

.caret {
  display: inline-block; width: 2px; height: 1em; margin-left: 2px;
  background: var(--firefly); vertical-align: text-bottom;
  animation: blink 1s step-end infinite;
}
@keyframes blink { 50% { opacity: 0; } }

.thinking { display: flex; align-items: center; gap: 4px; color: var(--text-faint); font-size: 13px; }
.thinking .dot { width: 5px; height: 5px; border-radius: 50%; background: var(--text-faint); animation: pulse 1.1s infinite; }
.thinking .dot:nth-child(2) { animation-delay: .18s; }
.thinking .dot:nth-child(3) { animation-delay: .36s; margin-right: 6px; }
@keyframes pulse { 0%, 60%, 100% { opacity: .25; } 30% { opacity: 1; } }

.cites { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; padding-top: 10px; border-top: 1px dashed var(--fern); }
.cite-chip {
  display: inline-flex; align-items: center; gap: 6px; max-width: 100%;
  padding: 3px 9px 3px 4px; border: 1px solid var(--fern); border-radius: 999px;
  background: transparent; color: var(--text-dim); font-size: 12px; cursor: pointer;
}
.cite-chip:hover { border-color: var(--text-faint); color: var(--text); }
.cite-chip.active { border-color: var(--firefly); background: rgba(139, 92, 246, .08); color: var(--text); }
.cite-no { display: inline-flex; align-items: center; justify-content: center; width: 16px; height: 16px; border-radius: 50%; background: rgba(139, 92, 246, .14); color: var(--firefly-soft); font-size: 10.5px; }
.cite-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 15em; }

.source { margin-top: 10px; padding: 12px 14px; border-radius: var(--radius); background: rgba(233, 241, 234, .7); border: 1px solid var(--fern); }
.source-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 6px; }
.source-title { font-size: 13px; font-weight: 600; }
.source-meta { color: var(--text-faint); font-size: 11.5px; white-space: nowrap; }
.source-body { color: var(--text-dim); font-size: 13px; line-height: 1.7; white-space: pre-wrap; max-height: 220px; overflow-y: auto; }
.job-link { display: inline-block; margin-top: 8px; color: var(--firefly-soft); font-size: 12.5px; }
.job-link:hover { text-decoration: underline; }

.composer { border-top: 1px solid var(--fern); padding-top: 10px; }
.composer .textarea { min-height: 56px; max-height: 220px; resize: none; }
.actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 8px; }
.counter { margin-right: auto; color: var(--text-faint); font-size: 11.5px; }
/* 长文本只做提醒，不拦发送：上限在服务端，前端不该替用户做决定 */
.counter--long { color: var(--firefly-soft); }
.gate { color: var(--text-faint); font-size: 12.5px; margin-bottom: 8px; }
.gate-link { color: var(--firefly-soft); }
.gate-link:hover { text-decoration: underline; }

.compact .intro { padding: 16px 2px 6px; }
.compact .intro-title { font-size: 1.25rem; }
.compact .bubble { max-width: 94%; font-size: 13.5px; }
.compact h3.heading { font-size: 1rem; }
.compact h4.heading { font-size: .9rem; }
</style>
