package seb.ai.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 引用条目：一条答案背后的某一块资料。
 *
 * <p>目前只有「岗位咨询」会产生引用——学习答疑是生成式的，不给引用。
 * 这不算功能缺失，而是刻意的：生成式回答如果硬套一段课程原文当出处，
 * 会变成「看起来有据、实际无关」的假溯源，比不给出处更糟。
 *
 * <p>字段设计上有个刻意的取舍：{@code content} 直接带全文（单块上限 800 字，
 * 四条最多 3.2KB），而不是只给摘要、点击时再请求一次详情接口。
 * 换来的是「点开原文」零延迟，以及引用卡片可以在断网重放时自证。
 */
@Data
@Builder
public class CitationVO {

    /** 分块ID，向量库中的文档 id */
    private Long chunkId;

    /** 文档ID */
    private Long docId;

    /** 块序号，从 0 开始 */
    private Integer chunkNo;

    /** 文档标题；岗位类是「公司 · 岗位名」 */
    private String title;

    /** 来源类型：1 课程章节 / 2 职位描述 / 3 上传文档 */
    private Integer sourceType;

    /** 关联职位ID；非职位来源为 null，前端据此决定能否跳到 /job/{id} */
    private Long jobId;

    /** 相关度（优先取重排分） */
    private Double score;

    /** 摘要，列表里展示 */
    private String excerpt;

    /** 块原文，展开时渲染 */
    private String content;
}
