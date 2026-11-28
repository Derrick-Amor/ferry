package seb.ai.controller;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import seb.ai.dto.AssistantChatDTO;
import seb.ai.service.AssistantService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能助教接口。
 *
 * <p>事件协议（顺序固定）：
 * <pre>
 * event: citations   {"citations":[...],"rejected":false,"hitCount":4}
 * event: delta       {"text":"多"}
 * event: delta       {"text":"态"}
 * event: done        {"elapsedMs":2431,"model":"Qwen/Qwen2.5-7B-Instruct","hitCount":4,"rejected":false}
 * event: error       {"message":"回答生成失败，请稍后再试。"}
 * </pre>
 *
 * <p>三条约定值得写下来：
 * <ol>
 *   <li>{@code citations} 永远先于第一个 {@code delta}，因为引用由检索决定、不由模型决定；</li>
 *   <li>所有 {@code data} 都是 JSON 而不是裸文本——答案里的换行会破坏 SSE 的行协议，
 *       包一层 JSON 就没有这个坑；</li>
 *   <li>响应头带 {@code X-Accel-Buffering: no}，让 Nginx 不要缓冲 SSE。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    /** 与前端 AbortController 的超时保持一致（180s）；真到这一步说明模型卡死 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    @Resource
    private AssistantService assistantService;

    /**
     * 提问并以 SSE 流式返回答案。
     *
     * @param dto      提问请求
     * @param response 用于关闭 Nginx 缓冲
     * @return SSE 发射器
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Validated @RequestBody AssistantChatDTO dto, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");

        long start = System.currentTimeMillis();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 检索与重排是同步的：它的结果决定有没有资格调用生成模型
        AssistantService.Session session = assistantService.prepare(dto);

        Map<String, Object> head = new LinkedHashMap<>();
        head.put("citations", session.citations());
        head.put("rejected", session.rejected());
        head.put("hitCount", session.hitCount());
        // 拒答时前端的空态要能解释「差多少」，这个分数就是解释
        head.put("topScore", session.topScore());
        if (!send(emitter, "citations", head)) {
            emitter.complete();
            return emitter;
        }

        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("elapsedMs", 0L);
        done.put("model", session.model());
        done.put("hitCount", session.hitCount());
        done.put("rejected", session.rejected());

        Disposable disposable = session.answer().subscribe(
                delta -> {
                    if (closed.get()) {
                        return;
                    }
                    // 推送失败通常意味着浏览器已经关了页面，没必要继续消费上游流
                    if (!send(emitter, "delta", Map.of("text", delta))) {
                        closed.set(true);
                        dispose(subscription);
                    }
                },
                error -> {
                    if (closed.compareAndSet(false, true)) {
                        log.warn("[assistant] 生成中断：{}", error.getMessage());
                        send(emitter, "error", Map.of("message", "回答生成失败，请稍后再试。"));
                        emitter.complete();
                    }
                },
                () -> {
                    if (closed.compareAndSet(false, true)) {
                        done.put("elapsedMs", System.currentTimeMillis() - start);
                        send(emitter, "done", done);
                        emitter.complete();
                    }
                });
        subscription.set(disposable);
        // 订阅可能在 set 之前就同步结束（拒答时是 Flux.just），这里补一次清理
        if (closed.get()) {
            dispose(subscription);
        }

        emitter.onTimeout(() -> {
            closed.set(true);
            dispose(subscription);
            emitter.complete();
        });
        emitter.onError(e -> {
            closed.set(true);
            dispose(subscription);
        });
        emitter.onCompletion(() -> {
            closed.set(true);
            dispose(subscription);
        });
        return emitter;
    }

    /**
     * 发送一个 SSE 事件。失败不抛异常——客户端断开是常态，不该污染业务日志。
     *
     * @param emitter 发射器
     * @param name    事件名
     * @param data    数据（会被序列化成 JSON）
     * @return true 表示发送成功
     */
    private boolean send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            log.debug("[assistant] SSE 发送失败（多半是客户端已断开）：{}", e.getMessage());
            return false;
        }
    }

    private void dispose(AtomicReference<Disposable> ref) {
        Disposable d = ref.get();
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }
}
