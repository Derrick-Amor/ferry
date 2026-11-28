package seb.ai.service.impl;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import seb.ai.model.ChunkHit;
import seb.ai.model.JobCriteria;
import seb.ai.service.KnowledgeRetrievalService;
import seb.ai.service.RerankService;
import seb.entity.Job;
import seb.entity.KnowledgeChunk;
import seb.entity.KnowledgeDoc;
import seb.mapper.JobMapper;
import seb.mapper.KnowledgeChunkMapper;
import seb.mapper.KnowledgeDocMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@link KnowledgeRetrievalService} 的实现。
 *
 * <p>四步，顺序不能换：
 * <ol>
 *   <li><b>向量召回</b>：{@code topK=8}。把 {@code sourceType} 作为过滤表达式下推给
 *       Redis——岗位咨询只允许命中职位描述，不能让课程正文混进来。过滤是索引级的，
 *       不是召回后再筛，所以不会出现「岗位被教程挤掉」。</li>
 *   <li><b>回补原文</b>：拿 {@code Document.getId()}（= chunkId）批量 {@code IN} 查
 *       {@code knowledge_chunk}，再查 {@code knowledge_doc} 拿标题与归属。
 *       这一步同时把「文档已禁用/已删除」的脏命中过滤掉。</li>
 *   <li><b>重排</b>：把候选块原文送 bge-reranker，按 0~1 相关度重排并截到 topN。</li>
 *   <li><b>降级</b>：重排服务不可用时保持向量序，{@code rerankScore} 留空，
 *       由上层用「无重排」的策略决定是否拒答。</li>
 * </ol>
 */
@Service
public class KnowledgeRetrievalServiceImpl implements KnowledgeRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrievalServiceImpl.class);

    /** 职位来源类型 */
    private static final int SOURCE_TYPE_JOB = 2;

    @Resource
    private VectorStore vectorStore;

    @Resource
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private RerankService rerankService;

    @Resource
    private JobMapper jobMapper;

    @Value("${ferry.ai.retrieve-top-k:8}")
    private int defaultTopK;

    @Value("${ferry.ai.rerank-top-n:4}")
    private int defaultTopN;

    @Override
    public List<ChunkHit> retrieve(String query, Integer sourceType, JobCriteria criteria, int topK, int topN) {
        int k = topK > 0 ? topK : defaultTopK;
        int n = topN > 0 ? topN : defaultTopN;
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 1) 向量召回
        SearchRequest.Builder request = SearchRequest.builder().query(query).topK(k);
        if (sourceType != null) {
            // 岗位咨询必须把课程正文挡在外面：问「有没有 Java 岗」时，
            // 一段讲 Java 语法的课程正文在语义上比岗位描述更接近问题
            request.filterExpression("sourceType == " + sourceType);
        }
        List<Document> docs = vectorStore.similaritySearch(request.build());
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        // 保持召回顺序，同时记下向量分：重排降级时它就是排序依据
        Map<Long, Double> vectorScores = new LinkedHashMap<>();
        for (Document doc : docs) {
            Long chunkId = parseId(doc.getId());
            if (chunkId == null || vectorScores.containsKey(chunkId)) {
                continue;
            }
            Object raw = doc.getMetadata() == null ? null : doc.getMetadata().get("vector_score");
            vectorScores.put(chunkId, raw instanceof Number num ? num.doubleValue() : 0d);
        }
        if (vectorScores.isEmpty()) {
            return List.of();
        }

        // 2) 回补原文：以 MySQL 为准，向量库只负责「找得到」
        List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectBatchIds(vectorScores.keySet());
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeChunk> chunkMap = new HashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            chunkMap.put(chunk.getId(), chunk);
        }
        List<Long> docIds = chunks.stream().map(KnowledgeChunk::getDocId).distinct().toList();
        Map<Long, KnowledgeDoc> docMap = new HashMap<>();
        for (KnowledgeDoc doc : knowledgeDocMapper.selectBatchIds(docIds)) {
            docMap.put(doc.getId(), doc);
        }

        List<ChunkHit> hits = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : vectorScores.entrySet()) {
            KnowledgeChunk chunk = chunkMap.get(entry.getKey());
            if (chunk == null) {
                continue;
            }
            KnowledgeDoc doc = docMap.get(chunk.getDocId());
            // 文档被禁用或已删除：向量还在，但不该再被引用
            if (doc == null || doc.getStatus() == null || doc.getStatus() != 1) {
                continue;
            }
            ChunkHit hit = new ChunkHit();
            hit.setChunkId(chunk.getId());
            hit.setDocId(doc.getId());
            hit.setTitle(doc.getTitle());
            hit.setSourceType(doc.getSourceType());
            hit.setJobId(doc.getJobId());
            hit.setChunkNo(chunk.getChunkNo());
            hit.setContent(chunk.getContent());
            hit.setVectorScore(entry.getValue());
            hits.add(hit);
        }
        if (hits.isEmpty()) {
            return List.of();
        }

        // 3) 结构化过滤：城市 / 方向 / 经验是硬条件，不能靠语义“差不多”
        if (criteria != null && criteria.hasAny()) {
            hits = filterByCriteria(hits, criteria);
            log.info("[retrieve] 条件 {} 命中 {} 条", criteria, hits.size());
            if (hits.isEmpty()) {
                return List.of();
            }
        }

        // 4) 重排
        List<String> contents = hits.stream().map(ChunkHit::getContent).toList();
        List<RerankService.Score> scores = rerankService.rerank(query, contents, n);
        if (scores.isEmpty()) {
            // 5) 降级：重排不可用时按向量序截断，rerankScore 保持 null
            log.warn("[retrieve] 重排不可用，按向量序返回 {} 条", Math.min(n, hits.size()));
            return hits.size() > n ? new ArrayList<>(hits.subList(0, n)) : hits;
        }

        List<ChunkHit> ranked = new ArrayList<>(scores.size());
        for (RerankService.Score score : scores) {
            ChunkHit hit = hits.get(score.index());
            hit.setRerankScore(score.score());
            ranked.add(hit);
        }
        // 岗位文档按「基本信息 / 岗位描述」切成两块，两块都命中的话
        // 同一个岗位会在引用列表里出现两次，回答里也会被引两次（[1][3]）。
        // 岗位场景按 jobId 去重，只留分最高的那一块。
        if (Integer.valueOf(SOURCE_TYPE_JOB).equals(sourceType)) {
            ranked = dedupeByJob(ranked);
        }
        log.info("[retrieve] sourceType={} 候选 {} 条，重排保留 {} 条，top={}",
                sourceType, hits.size(), ranked.size(),
                ranked.isEmpty() ? "-" : String.format("%.4f", ranked.get(0).getRerankScore()));
        return ranked;
    }

    /**
     * 按职位去重：同一岗位只保留相关度最高的那一个分块。
     *
     * @param hits 已按相关度降序的命中
     * @return 去重后的命中
     */
    private List<ChunkHit> dedupeByJob(List<ChunkHit> hits) {
        List<ChunkHit> kept = new ArrayList<>(hits.size());
        Set<Long> seenJobs = new HashSet<>();
        for (ChunkHit hit : hits) {
            Long jobId = hit.getJobId();
            if (jobId == null) {
                kept.add(hit);
                continue;
            }
            if (seenJobs.add(jobId)) {
                kept.add(hit);
            }
        }
        return kept;
    }

    /**
     * 按职位字段过滤命中块。
     *
     * <p>命中块只带 {@code jobId}，城市与方向在 {@code job} 表上，
     * 所以这里批量捞一次职位再判断。24 条岗位的量级下，这比给向量库
     * 再加 4 个 metadata 字段（还要整库重 embedding）划算得多；
     * 岗位涨到几千条时，应该把条件写进 {@code filterExpression} 下推给 Redis。
     *
     * @param hits     命中块
     * @param criteria 硬条件
     * @return 过滤后的命中块
     */
    private List<ChunkHit> filterByCriteria(List<ChunkHit> hits, JobCriteria criteria) {
        List<Long> jobIds = hits.stream()
                .map(ChunkHit::getJobId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (jobIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Job> jobMap = new HashMap<>();
        for (Job job : jobMapper.selectBatchIds(jobIds)) {
            jobMap.put(job.getId(), job);
        }
        List<ChunkHit> kept = new ArrayList<>(hits.size());
        for (ChunkHit hit : hits) {
            if (criteria.matches(jobMap.get(hit.getJobId()))) {
                kept.add(hit);
            }
        }
        return kept;
    }

    /**
     * 把向量 id 解析成分块主键。非数字 id 直接丢弃——
     * 换了索引实现或写入了脏数据时，宁可少一条，也不能让整个请求 500。
     *
     * @param id 向量库文档 id
     * @return 分块ID，无法解析时返回 null
     */
    private Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            log.warn("[retrieve] 忽略非数字向量 id：{}", id);
            return null;
        }
    }
}
