package seb.ai.service;

import seb.ai.model.ChunkHit;
import seb.ai.model.JobCriteria;

import java.util.List;

/**
 * 知识检索：向量召回 → 回补原文 → 重排。
 *
 * <p>只服务于「必须有据可查」的场景（当前是岗位咨询）。学习答疑不走这里——
 * 那里需要的是讲解能力，不是引用能力，见 {@link AssistantService} 的 scope 说明。
 *
 * <p>链路里有一处容易被忽略的事实：Spring AI 的 {@code similaritySearch} 只保证
 * {@link org.springframework.ai.document.Document#getId()} 是写入时给的 id
 * （本项目里就是 {@code knowledge_chunk.id}），业务 metadata 不一定回传。
 * 所以这里统一拿 id 回 MySQL 查一遍，既拿到 {@code title/jobId}，也顺带过滤掉
 * 「已禁用」或「已被删除」的脏命中。
 */
public interface KnowledgeRetrievalService {

    /**
     * 检索与问题相关的知识块。
     *
     * @param query      用户问题（多轮时会拼接上一轮问题，用于消解指代）
     * @param sourceType 来源类型：1 课程章节 / 2 职位描述；为 null 时全库检索
     * @param criteria   岗位硬条件（城市/方向/经验）；非岗位场景传 null
     * @param topK       向量召回条数
     * @param topN       重排后保留条数
     * @return 按相关度降序的命中；无命中返回空列表
     */
    List<ChunkHit> retrieve(String query, Integer sourceType, JobCriteria criteria, int topK, int topN);
}
