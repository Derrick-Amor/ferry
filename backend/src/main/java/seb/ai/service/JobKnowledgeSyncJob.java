package seb.ai.service;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 岗位同步的定时任务。
 *
 * <p>为什么需要它：{@code job} 表是「岗位咨询」的唯一事实来源，但知识库里的岗位文档
 * 是同步出来的副本。企业上架一条新岗位、改一次薪资，如果不重新同步，助手会继续按
 * 旧记录回答——而它自己并不知道自己过期了。这比答错更危险，因为看起来很有据可查。
 *
 * <p>默认每天凌晨 3 点跑一次。同步本身用 SHA-256 判断内容有没有变，
 * 没变就是空跑（50 条岗位的查询 + 哈希比对，毫秒级）；
 * 只有真有变更时才触发索引重建，不会每天白烧一遍 embedding。
 */
@Component
public class JobKnowledgeSyncJob {

    private static final Logger log = LoggerFactory.getLogger(JobKnowledgeSyncJob.class);

    @Resource
    private JobKnowledgeSyncService jobKnowledgeSyncService;

    @Resource
    private KnowledgeRebuildJob knowledgeRebuildJob;

    /** 是否启用，便于在不需要的环境关掉 */
    @Value("${ferry.ai.job-sync-enabled:true}")
    private boolean enabled;

    /**
     * 定时同步在架岗位。
     *
     * <p>cron 表达式可由 {@code ferry.ai.job-sync-cron} 覆盖，验证时把它调成
     * 每 20 秒一次（{@code 0/20 * * * * ?}）就能观察到执行日志。
     */
    @Scheduled(cron = "${ferry.ai.job-sync-cron:0 0 3 * * ?}")
    public void syncJobs() {
        if (!enabled) {
            log.debug("[job-sync] 定时同步已关闭（ferry.ai.job-sync-enabled=false）");
            return;
        }
        try {
            JobKnowledgeSyncService.SyncResult result = jobKnowledgeSyncService.sync();
            log.info("[job-sync] 定时任务：在架 {} 条，新增 {}，更新 {}，未变 {}",
                    result.jobTotal(), result.created(), result.updated(), result.skipped());
            if (result.hasChanges()) {
                boolean started = knowledgeRebuildJob.start();
                log.info("[job-sync] 检测到变更，触发索引重建：{}", started ? "已启动" : "已有任务在跑，跳过");
            }
        } catch (Exception e) {
            // 定时任务里抛异常会让后续调度静默失效（Spring 只打一条日志），必须自己吞掉并留痕
            log.error("[job-sync] 定时同步失败，本轮跳过", e);
        }
    }
}
