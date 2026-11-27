package seb.ai.service;

import seb.ai.model.ChunkResult;

import java.util.List;

/**
 * 知识分块服务：把一篇 Markdown 正文切成适合向量化的语义块。
 *
 * <p>分块粒度直接决定 RAG 的召回质量：块太大，检索命中后上下文里噪音多；
 * 块太小，一句完整结论会被切断。策略见实现类。
 */
public interface KnowledgeChunkService {

    /**
     * 按 Markdown 标题与句末标点切分正文。
     *
     * @param content 文档正文（Markdown）
     * @return 分块列表，按原文顺序，chunkNo 从 0 连续编号；入参为空时返回空列表
     */
    List<ChunkResult> split(String content);
}
