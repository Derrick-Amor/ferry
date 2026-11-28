package seb.ai.model;

import lombok.Data;

/**
 * 一条召回结果：向量命中 + MySQL 回补的原文与归属。
 *
 * <p>为什么不直接用 {@link org.springframework.ai.document.Document}：
 * 向量库返回的是「分数 + 文本」，而引用溯源需要 {@code docId / title / jobId}。
 * 这些字段虽然写进了向量 metadata，但以 MySQL 为准更可靠——
 * 重建索引失败、禁用文档、Redis 与库不同步时，只有库里的数据是权威的。
 *
 * @see seb.ai.service.KnowledgeRetrievalService
 */
@Data
public class ChunkHit {

    /** 分块ID，等于向量库中的文档 id */
    private Long chunkId;

    /** 所属文档ID */
    private Long docId;

    /** 文档标题，用于引用卡片 */
    private String title;

    /** 来源类型：1 课程章节 / 2 职位描述 / 3 上传文档 */
    private Integer sourceType;

    /** 关联职位ID；来源不是职位时为 null，前端据此决定能否跳转岗位详情 */
    private Long jobId;

    /** 块序号，从 0 开始 */
    private Integer chunkNo;

    /** 块原文 */
    private String content;

    /** 向量相似度（0~1，越大越相似） */
    private Double vectorScore;

    /** 重排相关度（0~1）；未重排时为 null */
    private Double rerankScore;
}
