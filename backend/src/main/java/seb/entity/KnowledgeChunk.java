package seb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识分块：一块对应向量库中的一条向量。
 *
 * <p>{@code chunkNo} 从 0 开始，与 {@code docId} 组成唯一键，
 * 保证同一文档的分块可以按序重建而不重复。
 *
 * @see KnowledgeDoc
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档ID */
    private Long docId;

    /** 块序号，从 0 开始 */
    private Integer chunkNo;

    /** 块原文 */
    private String content;

    /** 估算 token 数，用于控制上下文长度 */
    private Integer tokenCount;

    /** 向量库中的 ID，便于从命中的向量反查原文 */
    private String vectorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
