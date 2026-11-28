package seb.ai.service;

import java.util.List;

/**
 * 重排服务：对向量召回的候选块做二次排序。
 *
 * <p>为什么需要它：向量检索比的是「语义相近」，但相近不等于「能回答问题」。
 * 在中文语料上实测，同一个问题下相关块与无关块的余弦相似度经常挤在 0.6~0.75 之间，
 * 排序靠前的未必是答案所在的那一块；而 cross-encoder 类的重排模型直接对
 * 「问题 + 块」成对打分，区分度高得多（实测相关 0.99 / 无关 0.002），
 * 顺带还能直接拿来当拒答阈值。
 *
 * <p>实现必须容忍失败：重排是外部 HTTP 服务，超时、限流、欠费都可能发生。
 * 此时返回空列表，由调用方退回向量序——宁可答案差一点，也不能整个问答挂掉。
 */
public interface RerankService {

    /**
     * 对候选文档重排。
     *
     * @param query     用户问题
     * @param documents 候选块原文，顺序与召回结果一致
     * @param topN      保留条数
     * @return 按相关度降序的结果；服务不可用时返回空列表（调用方需自行降级）
     */
    List<Score> rerank(String query, List<String> documents, int topN);

    /**
     * 单条重排结果。
     *
     * @param index 该结果在入参 {@code documents} 中的下标
     * @param score 相关度，0~1，越大越相关
     */
    record Score(int index, double score) {
    }
}
