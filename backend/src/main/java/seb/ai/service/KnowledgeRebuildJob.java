package seb.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import seb.ai.model.IndexResult;
import seb.ai.model.RebuildStatus;
import seb.entity.KnowledgeDoc;
import seb.mapper.KnowledgeDocMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全量重建的后台任务宿主。
 *
 * <p>为什么必须异步：37 篇文档逐篇调 embedding，整个重建在一分钟以上，
 * 而 Nginx 的 {@code proxy_read_timeout} 只有 60 秒。同步做的话，
 * 用户在浏览器里等到的是 504，可后台其实还在跑——最糟的一种状态：
 * 事情做了，但没人知道做完了没有。
 *
 * <p>并发保护用 {@link AtomicBoolean} 而不是锁：重建正在跑时第二次点击
 * 应该立刻得到「已在执行」，而不是排队等一分钟再跑一遍。
 */
@Service
public class KnowledgeRebuildJob {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRebuildJob.class);

    /** 失败明细最多保留条数 */
    private static final int MAX_ERRORS = 10;

    @Resource
    private KnowledgeIndexService knowledgeIndexService;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final RebuildStatus status = new RebuildStatus();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "knowledge-rebuild");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 启动一次全量重建。
     *
     * @return true 表示本次调用启动了任务；false 表示已有任务在跑
     */
    public boolean start() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        // 先数一遍总数：让前端从第一秒就能显示「3/37」，
        // 而不是靠回调边跑边猜（那样会出现「10/11」这种看起来像 bug 的进度）
        long total = knowledgeDocMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getStatus, 1));
        synchronized (this) {
            status.setErrors(new ArrayList<>());
            status.setState(RebuildStatus.RUNNING);
            status.setStartedAt(System.currentTimeMillis());
            status.setFinishedAt(null);
            status.setElapsedMs(0);
            status.setDone(0);
            status.setTotal((int) total);
            status.setDocSuccess(0);
            status.setDocFailed(0);
            status.setChunkTotal(0);
            status.setVectorTotal(0);
            status.setCurrentTitle(null);
        }

        executor.submit(() -> {
            try {
                IndexResult result = knowledgeIndexService.rebuildAll((done, doc) -> {
                    synchronized (this) {
                        status.setDone(done);
                        status.setCurrentTitle(doc.getTitle());
                    }
                });
                synchronized (this) {
                    // 回调在「开始处理某篇」时触发，最后一篇不会被回调到，
                    // 所以这里用结果里的总数收口，避免结束时显示 36/37
                    status.setDone(result.getDocTotal());
                    status.setDocSuccess(result.getDocSuccess());
                    status.setDocFailed(result.getDocFailed());
                    status.setChunkTotal(result.getChunkTotal());
                    status.setVectorTotal(result.getVectorTotal());
                    status.setErrors(trim(result.getErrors()));
                    status.setState(result.getDocFailed() > 0 ? RebuildStatus.FAILED : RebuildStatus.DONE);
                }
            } catch (Exception e) {
                log.error("[index] 全量重建中断", e);
                synchronized (this) {
                    status.getErrors().add("重建中断：" + e.getMessage());
                    status.setState(RebuildStatus.FAILED);
                }
            } finally {
                synchronized (this) {
                    status.setCurrentTitle(null);
                    status.setFinishedAt(System.currentTimeMillis());
                    if (status.getStartedAt() != null) {
                        status.setElapsedMs(status.getFinishedAt() - status.getStartedAt());
                    }
                }
                running.set(false);
            }
        });
        return true;
    }

    /**
     * 读取当前进度快照。
     *
     * @return 快照
     */
    public synchronized RebuildStatus snapshot() {
        if (RebuildStatus.RUNNING.equals(status.getState()) && status.getStartedAt() != null) {
            status.setElapsedMs(System.currentTimeMillis() - status.getStartedAt());
        }
        return status;
    }

    /**
     * 是否正在重建。
     *
     * @return true 表示运行中
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 截断失败明细。
     *
     * @param errors 原始明细
     * @return 最多 {@value #MAX_ERRORS} 条
     */
    private List<String> trim(List<String> errors) {
        if (errors == null) {
            return new ArrayList<>();
        }
        return errors.size() <= MAX_ERRORS
                ? new ArrayList<>(errors)
                : new ArrayList<>(errors.subList(0, MAX_ERRORS));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
