package seb.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import seb.ai.model.IndexResult;
import seb.ai.service.JobKnowledgeSyncService;
import seb.ai.service.KnowledgeIndexService;
import seb.ai.service.KnowledgeRebuildJob;
import seb.common.Result;
import seb.entity.KnowledgeChunk;
import seb.entity.KnowledgeDoc;
import seb.mapper.KnowledgeChunkMapper;
import seb.mapper.KnowledgeDocMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 知识库运维接口（仅管理员，由 {@code JwtInterceptor} 的 {@code /api/admin/} 前缀统一拦截）。
 *
 * <p>重建分两种粒度：
 * <ul>
 *   <li><b>单篇同步</b>：改了一章内容就重建一章，几秒返回，结果直接可读；</li>
 *   <li><b>全量异步</b>：返回「已启动」，前端轮询 {@code /status} 看进度。
 *       全量要逐篇调 embedding，一分钟起步，同步做必然被 Nginx 60 秒超时截断。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    @Resource
    private KnowledgeRebuildJob knowledgeRebuildJob;

    @Resource
    private KnowledgeIndexService knowledgeIndexService;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Resource
    private JobKnowledgeSyncService jobKnowledgeSyncService;

    /**
     * 知识库现状 + 重建进度。
     *
     * @return 文档数、分块数、任务快照
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("job", knowledgeRebuildJob.snapshot());
        data.put("docCount", knowledgeDocMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getStatus, 1)));
        data.put("chunkCount", knowledgeChunkMapper.selectCount(null));
        data.put("jobDocCount", knowledgeDocMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getSourceType, 2)
                        .eq(KnowledgeDoc::getStatus, 1)));
        return Result.ok(data);
    }

    /**
     * 把在架职位同步成知识文档，并自动触发索引重建。
     *
     * <p>一条命令做两件事：MySQL 侧 upsert（同步岗位）+ 异步重建索引（向量化）。
     * 拆成两个接口让人容易只做一半——同步完忘了重建，检索里就永远少最新的岗位。
     *
     * @return 同步统计与重建状态
     */
    @PostMapping("/sync-jobs")
    public Result<Map<String, Object>> syncJobs() {
        JobKnowledgeSyncService.SyncResult result = jobKnowledgeSyncService.sync();
        boolean rebuildStarted = false;
        if (result.hasChanges()) {
            rebuildStarted = knowledgeRebuildJob.start();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sync", result);
        data.put("rebuildStarted", rebuildStarted);
        data.put("job", knowledgeRebuildJob.snapshot());
        return Result.ok(data);
    }

    /**
     * 启动全量重建（异步）。
     *
     * @return started=false 表示已有任务在跑，同时返回当前快照
     */
    @PostMapping("/rebuild")
    public Result<Map<String, Object>> rebuildAll() {
        boolean started = knowledgeRebuildJob.start();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("started", started);
        data.put("job", knowledgeRebuildJob.snapshot());
        return Result.ok(data);
    }

    /**
     * 重建单篇文档（同步）。
     *
     * @param docId 文档ID
     * @return 该文档的统计结果
     */
    @PostMapping("/{docId}/rebuild")
    public Result<IndexResult> rebuildOne(@PathVariable Long docId) {
        return Result.ok(knowledgeIndexService.rebuildOne(docId));
    }
}
