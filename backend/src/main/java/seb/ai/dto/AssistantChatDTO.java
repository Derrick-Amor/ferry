package seb.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 助教提问请求。
 *
 * <p>两种 scope 走的是两条完全不同的链路：
 * <ul>
 *   <li>{@link #SCOPE_TUTOR} 学习答疑：生成式，不检索、不引用、不拒答；</li>
 *   <li>{@link #SCOPE_JOB} 岗位咨询：RAG，只依据系统里的职位记录，查不到就拒答。</li>
 * </ul>
 *
 * <p>多轮上下文由前端携带、后端不落库：{@code history} 只包含最近几轮，
 * 服务端既不做会话管理，也不回写数据库。
 */
@Data
public class AssistantChatDTO {

    public static final String SCOPE_TUTOR = "tutor";
    public static final String SCOPE_JOB = "job";

    /** 问题 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过 500 字")
    private String question;

    /**
     * 问答类型：tutor 学习答疑 / job 岗位咨询。
     * 缺省按 tutor 处理——宁可给一个可能不严谨的讲解，也不要莫名其妙拒答。
     */
    @Pattern(regexp = "tutor|job", message = "scope 只能是 tutor 或 job")
    private String scope;

    /**
     * 场景提示，例如「《Java 基础入门》第 3 章」。
     *
     * <p>只用于学习答疑：让模型知道用户正在学什么，从而挑重点、给例子。
     * 它<b>不是</b>检索过滤条件，也不参与拒答。
     */
    @Size(max = 60, message = "场景提示不能超过 60 字")
    private String context;

    /** 历史对话，按时间正序；最多 8 条（4 轮） */
    @Size(max = 8, message = "只能携带最近 4 轮对话")
    @Valid
    private List<Turn> history;

    /**
     * 一轮对话。
     */
    @Data
    public static class Turn {

        /** 角色：user / assistant */
        @Pattern(regexp = "user|assistant", message = "role 只能是 user 或 assistant")
        private String role;

        /** 内容 */
        @Size(max = 1000, message = "单条历史不能超过 1000 字")
        private String content;
    }
}
