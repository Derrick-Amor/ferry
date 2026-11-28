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
 * 知识文档元数据：一条记录对应一份可被 RAG 检索的资料。
 *
 * <p>分块原文落在 {@link KnowledgeChunk}，向量落在 Redis Stack，
 * 通过 {@code knowledge_chunk.vector_id} 与向量库中的条目对应。
 *
 * @see KnowledgeChunk
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档标题，如「第二章 变量、数据类型与运算符」 */
    private String title;

    /** 文档正文（Markdown），分块与重建索引的唯一数据源 */
    private String content;

    /** 来源类型：1课程章节 2职位描述 3上传文档 */
    private Integer sourceType;

    /** 关联课程ID，可空 */
    private Long courseId;

    /** 关联章节ID，可空 */
    private Long chapterId;

    /** 关联职位ID，可空（来源为职位描述时填写） */
    private Long jobId;

    /** 内容 SHA-256（64 位十六进制），用于判断内容是否变化、是否需要重建索引 */
    private String contentHash;

    /** 分块数量 */
    private Integer chunkCount;

    /** 状态：0禁用 1启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
