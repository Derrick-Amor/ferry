package seb.ai.service;

import reactor.core.publisher.Flux;
import seb.ai.dto.AssistantChatDTO;
import seb.ai.vo.CitationVO;

import java.util.List;

/**
 * 智能助教：把「检索 → 重排 → 生成」串成一次问答。
 */
public interface AssistantService {

    /**
     * 准备一次问答：同步做完检索与重排，返回引用列表和待消费的答案流。
     *
     * <p>为什么把「准备」和「流式输出」分开：引用不依赖模型输出，
     * 检索完成的那一刻就已经确定了。先推引用、再吐正文，
     * 意味着即使模型胡编，引用面板上的来源也不会被篡改——
     * 这是「可溯源」和「看起来像有来源」的区别。
     *
     * @param dto 提问请求
     * @return 会话：引用、答案流、是否拒答
     */
    Session prepare(AssistantChatDTO dto);

    /**
     * 一次问答的上下文。
     *
     * @param citations 引用列表，已按相关度降序；拒答时为空
     * @param answer    答案流（逐段文本）
     * @param rejected  是否因「资料不足」而拒答（此时没有调用生成模型）
     * @param hitCount  重排后保留的条数
     * @param topScore  最高相关度；拒答时也返回，便于前端解释「为什么没答」
     * @param model     生成模型名，便于前端与日志核对
     */
    record Session(List<CitationVO> citations, Flux<String> answer,
                   boolean rejected, int hitCount, double topScore, String model) {
    }
}
