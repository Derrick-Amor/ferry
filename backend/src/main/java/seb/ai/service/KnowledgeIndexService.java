package seb.ai.service;

import seb.ai.model.IndexResult;
import seb.entity.KnowledgeDoc;

import java.util.function.BiConsumer;

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
     * 重建全部启用文档的索引，并在每篇开始前回调进度。
     *
     * <p>回调是为了让管理端能看到「正在重建 12/37」而不是干等几十秒。
     * 回调在重建线程里执行，实现方不要在里面做重活。
     *
     * @param onDoc 进度回调，参数为（已完成数, 当前文档）；可为 null
     * @return 汇总统计
     */
    IndexResult rebuildAll(BiConsumer<Integer, KnowledgeDoc> onDoc);

    /**
     * 重建单篇文档的索引。幂等：会先清掉该文档旧的向量与分块。
     *
     * @param docId 文档ID
     * @return 该文档的统计结果
     */
    IndexResult rebuildOne(Long docId);
}
