package seb.ai.service.impl;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import seb.ai.dto.AssistantChatDTO;
import seb.ai.model.ChunkHit;
import seb.ai.model.JobCriteria;
import seb.ai.service.AssistantService;
import seb.ai.service.JobQueryParser;
import seb.ai.service.KnowledgeRetrievalService;
import seb.ai.vo.CitationVO;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * {@link AssistantService} 的实现：两条链路，按 {@code scope} 分流。
 *
 * <p><b>为什么学习答疑不走 RAG。</b> 最初的实现对所有问题都先检索课程正文，
 * 检索不到就拒答。结果「Java 的多态怎么理解」这种最该被讲清楚的问题，
 * 因为生成课程正文时没写「多态」两个字，直接被判成「课程中没有相关内容」。
 * 这是把「必须有据可查」错当成了所有问答的前提——课程讲解的价值在于讲透，
 * 不在于只能引用原文。所以 tutor 链路不检索、不引用、不拒答。
 *
 * <p><b>为什么岗位咨询必须走 RAG。</b> 薪资、城市、学历、任职要求这些字段
 * 一旦由模型自由发挥，就是编造招聘信息。这类问题只有一个可信来源：
 * 系统里存着的那条岗位记录。所以 job 链路检索、重排、阈值拒答、带引用。
 *
 * <p>两条链路共用同一套 SSE 协议与前端组件，差别只在后端。
 */
@Service
public class AssistantServiceImpl implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);

    /** 岗位检索的来源类型：1 课程章节 / 2 职位描述 */
    private static final int SOURCE_TYPE_JOB = 2;

    /** 岗位链路资料不足时的固定回答 */
    private static final String JOB_REJECT_ANSWER = "系统里暂时没有符合条件的岗位。";

    /** 摘要长度 */
    private static final int EXCERPT_LEN = 120;

    /** 场景提示的最大长度，防止前端塞进超长文本干扰提示词 */
    private static final int CONTEXT_MAX = 60;

    /** 指代词：命中则说明这一问很可能在追问上一轮 */
    private static final Set<String> FOLLOW_UP_TOKENS = Set.of("它", "这个", "那个", "这些", "那些", "还有", "呢");

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Resource
    private JobQueryParser jobQueryParser;

    @Value("${ferry.ai.retrieve-top-k:8}")
    private int topK;

    @Value("${ferry.ai.job-retrieve-top-k:60}")
    private int jobTopK;

    @Value("${ferry.ai.rerank-top-n:4}")
    private int topN;

    /** 重排分的拒答阈值（岗位场景标定） */
    @Value("${ferry.ai.reject-score-threshold:0.38}")
    private double rejectScoreThreshold;

    /** 重排不可用时退回向量分的拒答阈值 */
    @Value("${ferry.ai.reject-vector-threshold:0.55}")
    private double rejectVectorThreshold;

    @Value("${ferry.ai.chat-model:Qwen/Qwen2.5-7B-Instruct}")
    private String chatModel;

    @Override
    public Session prepare(AssistantChatDTO dto) {
        String scope = dto.getScope();
        if (AssistantChatDTO.SCOPE_JOB.equals(scope)) {
            return prepareJob(dto);
        }
        return prepareTutor(dto);
    }

    // ------------------------------------------------------------------
    // 学习答疑：生成式
    // ------------------------------------------------------------------

    /**
     * 学习答疑：不检索、不拒答。
     *
     * @param dto 请求
     * @return 会话（引用恒为空）
     */
    private Session prepareTutor(AssistantChatDTO dto) {
        String context = sanitize(dto.getContext());
        log.info("[assistant] scope=tutor context={}", context == null ? "-" : context);

        ChatClient.ChatClientRequestSpec spec = chatClientBuilder.build()
                .prompt()
                .system(buildTutorPrompt(context));

        List<Message> history = toMessages(dto.getHistory(), false);
        if (!history.isEmpty()) {
            spec.messages(history);
        }
        spec.user(dto.getQuestion());

        Flux<String> answer = spec.stream().content().timeout(Duration.ofSeconds(90));
        return new Session(List.of(), answer, false, 0, 0d, chatModel);
    }

    /**
     * 学习助教的提示词。
     *
     * <p>这里刻意不写「只根据资料回答」——那正是上一版的问题。
     * 但也不能放任模型胡说：教学场景里讲错一个概念，比说「我不确定」有害得多，
     * 所以第 2 条要求它区分「确定」与「可能有版本差异」，并且禁止编造 API 与版本号。
     *
     * @param context 场景提示，可为 null
     * @return system prompt
     */
    private String buildTutorPrompt(String context) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("你是「渡口 Ferry」的学习助教，面向正在学编程、准备找工作的学生。\n\n")
                .append("回答要求：\n")
                .append("1. 先给结论，再展开解释；需要时给一个最小可运行的例子，别堆砌大段代码。\n")
                .append("2. 可以用你自己的知识，但要区分「确定的」和「可能有版本差异的」；")
                .append("不确定就直说「这点我不确定」，不要编造 API、版本号、公司数据。\n")
                .append("3. 学生问概念（多态、闭包、索引、依赖注入）时，讲清「为什么」和「什么时候用」，")
                .append("不要只背定义。\n")
                .append("4. 用中文。长度看问题难度：概念解释 200~500 字，代码题可以更长。\n")
                .append("5. 不要写「根据资料」「综上所述」这类套话。\n")
                .append("6. 问题与编程学习、求职无关时，礼貌地把话题带回来，不要长篇闲聊。\n");
        if (context != null) {
            sb.append("\n背景：用户当前在学「").append(context)
                    .append("」。回答时可以结合这个背景挑重点，但不必强行联系。\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 岗位咨询：RAG
    // ------------------------------------------------------------------

    /**
     * 岗位咨询：检索 → 重排 → 阈值拒答 → 带引用生成。
     *
     * @param dto 请求
     * @return 会话
     */
    private Session prepareJob(AssistantChatDTO dto) {
        String query = buildRetrievalQuery(dto.getQuestion(), dto.getHistory());
        // 岗位库里只有几十个分块，索性全取回来再做结构化过滤：
        // 如果只取 top8，一旦城市/方向条件把前 8 条全筛掉，就会误判成「没有岗位」，
        // 而实际上第 9 条就是他要的。岗位涨到几千条时再改成索引级过滤。
        JobCriteria criteria = jobQueryParser.parse(dto.getQuestion());
        List<ChunkHit> hits = knowledgeRetrievalService.retrieve(
                query, SOURCE_TYPE_JOB, criteria, jobTopK, topN);
        double topScore = hits.isEmpty() ? 0d : rawTopScore(hits.get(0));

        // 带硬条件时：命中即答，不再用相关度阈值二次否定——
        // 用户问的是「杭州的岗位」，只要确实有杭州的岗位，就该列出来，
        // 哪怕问句本身很泛、语义分数不高。
        boolean reject = hits.isEmpty() || (!criteria.hasAny() && shouldReject(hits));
        if (reject) {
            log.info("[assistant] scope=job 拒答 条件={} 命中={} 分={}",
                    criteria, hits.size(), hits.isEmpty() ? "-" : String.format("%.4f", topScore));
            return new Session(List.of(), Flux.just(JOB_REJECT_ANSWER), true, hits.size(), topScore, chatModel);
        }

        List<CitationVO> citations = new ArrayList<>(hits.size());
        for (ChunkHit hit : hits) {
            citations.add(toCitation(hit));
        }
        log.info("[assistant] scope=job 作答 引用={} 条 top={}",
                citations.size(), String.format("%.4f", topScore));

        ChatClient.ChatClientRequestSpec spec = chatClientBuilder.build()
                .prompt()
                .system(buildJobPrompt(hits));

        List<Message> history = toMessages(dto.getHistory(), true);
        if (!history.isEmpty()) {
            spec.messages(history);
        }
        spec.user(dto.getQuestion());

        Flux<String> answer = spec.stream().content().timeout(Duration.ofSeconds(90));
        return new Session(citations, answer, false, hits.size(), topScore, chatModel);
    }

    /**
     * 岗位顾问的提示词。
     *
     * <p>规则只有一条核心：<b>记录里没有的字段不许猜</b>。
     * 招聘信息的特点是「差一个字都不算数」——薪资少写 5k、城市写错，
     * 对用户就是误导。所以宁可拒答，也不允许用行业常识补全。
     *
     * @param hits 命中的岗位块
     * @return system prompt
     */
    private String buildJobPrompt(List<ChunkHit> hits) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("你是「渡口 Ferry」的岗位顾问。你只能依据下面提供的岗位记录回答，不能使用记录以外的信息。\n\n")
                .append("输出格式（必须遵守）：\n")
                .append("1. 每句结论后面标引用编号，写成「…… [1]」；多条依据写「…… [1][2]」。\n")
                .append("2. 编号只能是记录里出现过的 [1]~[").append(hits.size()).append("]，不许编造。\n")
                .append("3. 记录里没有的字段（薪资、城市、学历、经验、公司），不要猜，也不要用行业常识补充。\n")
                .append("4. 没有符合条件的岗位时，只回答一句：「系统里暂时没有符合条件的岗位。」\n")
                .append("5. 可以帮用户对比多条岗位，但只能基于记录里的字段。\n")
                .append("6. 用中文，控制在 300 字以内。\n\n")
                .append("岗位记录：\n");
        for (int i = 0; i < hits.size(); i++) {
            ChunkHit hit = hits.get(i);
            sb.append('[').append(i + 1).append("] 《").append(hit.getTitle()).append("》\n")
                    .append(hit.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 构造检索用的 query。
     *
     * <p>多轮对话有个绕不开的问题：用户第二句往往只有「那它呢」四个字，
     * 直接拿去做向量检索，召回的基本是噪声。这里用「短问句或含指代词」作为追问信号，
     * 命中就把上一轮问题拼在前面。
     *
     * @param question 当前问题
     * @param history  历史对话
     * @return 检索用 query
     */
    private String buildRetrievalQuery(String question, List<AssistantChatDTO.Turn> history) {
        String current = question == null ? "" : question.trim();
        if (history == null || history.isEmpty()) {
            return current;
        }
        String lastUser = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            AssistantChatDTO.Turn turn = history.get(i);
            if (turn != null && "user".equals(turn.getRole())
                    && turn.getContent() != null && !turn.getContent().isBlank()) {
                lastUser = turn.getContent().trim();
                break;
            }
        }
        if (lastUser == null) {
            return current;
        }
        boolean followUp = current.length() <= 15 || FOLLOW_UP_TOKENS.stream().anyMatch(current::contains);
        if (!followUp) {
            return current;
        }
        return lastUser + " " + current;
    }

    /**
     * 是否拒答。有重排分时用重排阈值，否则退回向量阈值——
     * 两套阈值不能混用：0.38 对向量分来说意味着「几乎全部放行」。
     *
     * @param hits 命中列表（按相关度降序）
     * @return true 表示资料不足
     */
    private boolean shouldReject(List<ChunkHit> hits) {
        ChunkHit top = hits.get(0);
        boolean hasRerank = top.getRerankScore() != null;
        return rawTopScore(top) < (hasRerank ? rejectScoreThreshold : rejectVectorThreshold);
    }

    /**
     * 取最高分：优先重排分，没有重排分时退回向量分。
     *
     * @param hit 首条命中
     * @return 分数，缺失时为 0
     */
    private double rawTopScore(ChunkHit hit) {
        Double score = hit.getRerankScore() != null ? hit.getRerankScore() : hit.getVectorScore();
        return score == null ? 0d : score;
    }

    /**
     * 把前端带来的历史转成 Spring AI 的消息。
     *
     * @param history       历史对话
     * @param stripCitations 是否剥掉 [1][2]。只在岗位链路开启：
     *                       上一轮的编号指向上一轮的岗位记录，留着会让模型以为当前也有这些编号；
     *                       而学习答疑里的方括号可能是代码（arr[1]），不能动。
     * @return 消息列表
     */
    private List<Message> toMessages(List<AssistantChatDTO.Turn> history, boolean stripCitations) {
        List<Message> messages = new ArrayList<>();
        if (history == null) {
            return messages;
        }
        for (AssistantChatDTO.Turn turn : history) {
            if (turn == null || turn.getContent() == null || turn.getContent().isBlank()) {
                continue;
            }
            String content = turn.getContent().trim();
            if ("assistant".equals(turn.getRole())) {
                String text = stripCitations ? content.replaceAll("\\[\\d{1,2}]", "").trim() : content;
                messages.add(new AssistantMessage(text));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    /**
     * 命中块 → 引用条目。
     *
     * @param hit 命中
     * @return 引用
     */
    private CitationVO toCitation(ChunkHit hit) {
        return CitationVO.builder()
                .chunkId(hit.getChunkId())
                .docId(hit.getDocId())
                .chunkNo(hit.getChunkNo())
                .title(hit.getTitle())
                .sourceType(hit.getSourceType())
                .jobId(hit.getJobId())
                .score(hit.getRerankScore() != null ? hit.getRerankScore() : hit.getVectorScore())
                .excerpt(excerpt(hit.getContent()))
                .content(hit.getContent())
                .build();
    }

    /**
     * 生成摘要：去掉 Markdown 标题标记、压平空白，超长截断。
     *
     * @param content 块原文
     * @return 摘要
     */
    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() <= EXCERPT_LEN ? text : text.substring(0, EXCERPT_LEN) + "…";
    }

    /**
     * 清洗场景提示：去掉换行与控制字符，防止它被当成额外的指令行。
     *
     * @param context 原始值
     * @return 清洗后的值；空字符串返回 null
     */
    private String sanitize(String context) {
        if (context == null) {
            return null;
        }
        String text = context.replaceAll("[\\r\\n\\t]", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() <= CONTEXT_MAX ? text : text.substring(0, CONTEXT_MAX);
    }
}
