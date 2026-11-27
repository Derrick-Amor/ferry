package seb.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档分块结果（单块）。
 *
 * <p>由 {@code KnowledgeChunkService#split} 产出，是「原文 → 向量」链路的中间产物，
 * 不直接持久化：落库时再补上 docId 与自增主键。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkResult {

    /** 块序号，从 0 开始，与 docId 组成 knowledge_chunk 的唯一键 */
    private Integer chunkNo;

    /** 块正文 */
    private String content;

    /** 估算 token 数（字符数 / 1.5，粗略值，仅用于控制上下文长度） */
    private Integer tokenCount;

    /**
     * 按字符数粗略估算 token 数。
     *
     * <p>中文场景下 1 token 大致对应 1.5 个汉字，这里不引入分词库，
     * 目的是给后续「按预算截断上下文」一个量级正确的参考值。
     *
     * @param text 原文
     * @return 估算 token 数
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 1.5d);
    }
}
