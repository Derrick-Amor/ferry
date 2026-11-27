package seb.ai.service;

import seb.ai.model.IndexResult;

/**
 * 知识库索引服务：把 MySQL 里的文档正文分块、向量化，并同时落到
 * {@code knowledge_chunk}（原文）与 Redis 向量库（向量）。
 */
public interface KnowledgeIndexService {

    /**
     * 重建全部启用文档的索引。
     *
     * @return 汇总统计（文档数 / 分块数 / 向量数 / 失败明细）
     */
    IndexResult rebuildAll();

    /**
     * 重建单篇文档的索引。幂等：会先清掉该文档旧的向量与分块。
     *
     * @param docId 文档ID
     * @return 该文档的统计结果
     */
    IndexResult rebuildOne(Long docId);
}
