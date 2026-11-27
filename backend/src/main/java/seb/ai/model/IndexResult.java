package seb.ai.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库重建的统计结果。
 *
 * <p>用于观察「文档数 / 分块数 / 向量数」三者是否对得上——
 * 这三个数字相等或相近，才说明 MySQL 与 Redis 是一致的。
 */
@Data
public class IndexResult {

    /** 处理文档总数 */
    private int docTotal;

    /** 成功文档数 */
    private int docSuccess;

    /** 失败文档数 */
    private int docFailed;

    /** 写入的分块总数（= MySQL knowledge_chunk 行数增量） */
    private int chunkTotal;

    /** 写入的向量总数（= Redis 索引文档数增量） */
    private int vectorTotal;

    /** 耗时（毫秒） */
    private long elapsedMs;

    /** 失败明细，格式：「docId 标题 -> 原因」 */
    private List<String> errors = new ArrayList<>();

    /**
     * 累加另一份结果，便于 rebuildAll 汇总各文档。
     *
     * @param other 待累加的结果
     */
    public void merge(IndexResult other) {
        if (other == null) {
            return;
        }
        this.docTotal += other.docTotal;
        this.docSuccess += other.docSuccess;
        this.docFailed += other.docFailed;
        this.chunkTotal += other.chunkTotal;
        this.vectorTotal += other.vectorTotal;
        this.errors.addAll(other.errors);
    }

    @Override
    public String toString() {
        return "IndexResult{docTotal=" + docTotal
                + ", docSuccess=" + docSuccess
                + ", docFailed=" + docFailed
                + ", chunkTotal=" + chunkTotal
                + ", vectorTotal=" + vectorTotal
                + ", elapsedMs=" + elapsedMs
                + ", errors=" + errors.size() + '}';
    }
}
