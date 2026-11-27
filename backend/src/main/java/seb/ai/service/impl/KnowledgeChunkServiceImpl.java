package seb.ai.service.impl;

import org.springframework.stereotype.Service;
import seb.ai.model.ChunkResult;
import seb.ai.service.KnowledgeChunkService;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link KnowledgeChunkService} 的实现。
 *
 * <p>分块策略（三级）：
 * <ol>
 *   <li><b>按标题切</b>：以 {@code ##} 二级标题为语义边界切段。
 *       一节课的一个小节通常就是「一个完整知识点」，跨小节合并会把两个主题混进同一向量，
 *       检索时反而降低区分度。</li>
 *   <li><b>按句切</b>：单节超过 {@value #TARGET_MAX} 字时，再按句末标点
 *       （。！？；!?; 与换行）切成句子，贪心拼装到接近上限，避免把一句话劈开。</li>
 *   <li><b>保留重叠</b>：相邻块之间保留 {@value #OVERLAP} 字重叠，
 *       防止「关键句正好落在切分点」导致两边都拿不到完整语义。</li>
 * </ol>
 *
 * <p>只含标题、没有实质内容的块会被丢弃——这种块向量化后会把「章节名」变成高频噪声，
 * 检索时容易被无关问题召回。
 */
@Service
public class KnowledgeChunkServiceImpl implements KnowledgeChunkService {

    /** 单块目标上限（字）。512~800 是中文 RAG 的常用区间，这里取 800 保留上下文完整度 */
    private static final int TARGET_MAX = 800;

    /** 相邻块重叠字数 */
    private static final int OVERLAP = 50;

    /** 去掉标题后正文短于该阈值，判定为「只有标题的块」并丢弃 */
    private static final int MIN_BODY_LEN = 30;

    /** 在二级标题前切分（lookahead，保留标题本身在本段开头） */
    private static final Pattern SECTION_SPLIT = Pattern.compile("(?m)(?=^##\\s+)");

    @Override
    public List<ChunkResult> split(String content) {
        List<ChunkResult> result = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return result;
        }
        String normalized = content.replace("\r\n", "\n").trim();

        int chunkNo = 0;
        for (String rawSection : SECTION_SPLIT.split(normalized)) {
            String section = rawSection.trim();
            if (section.isEmpty()) {
                continue;
            }
            // 纯标题块（去掉 ## 行后几乎没有内容）直接丢弃
            if (stripHeading(section).length() < MIN_BODY_LEN) {
                continue;
            }
            if (section.length() <= TARGET_MAX) {
                result.add(new ChunkResult(chunkNo++, section, ChunkResult.estimateTokens(section)));
                continue;
            }
            for (String piece : packBySentence(section)) {
                String text = piece.trim();
                if (text.isEmpty()) {
                    continue;
                }
                result.add(new ChunkResult(chunkNo++, text, ChunkResult.estimateTokens(text)));
            }
        }
        return result;
    }

    /**
     * 去掉段首的 {@code ##} 标题行，只留正文，用于判断该段是否有实质内容。
     *
     * @param section 已按标题切好的段落
     * @return 去掉首行标题后的正文
     */
    private String stripHeading(String section) {
        int firstBreak = section.indexOf('\n');
        if (firstBreak < 0) {
            return section.startsWith("##") ? "" : section;
        }
        String firstLine = section.substring(0, firstBreak);
        String rest = section.substring(firstBreak + 1);
        return firstLine.trim().startsWith("##") ? rest.trim() : section;
    }

    /**
     * 把超长段落按句切分并贪心组装成若干块，块间保留 {@link #OVERLAP} 字重叠。
     *
     * @param text 段落原文
     * @return 组装后的块列表
     */
    private List<String> packBySentence(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();

        for (String sentence : splitSentences(text)) {
            String remain = sentence;
            // 单句本身超过上限：先冲刷当前缓冲，再硬切成上限长度的片段
            while (remain.length() > TARGET_MAX) {
                if (buf.length() > 0) {
                    chunks.add(buf.toString());
                    buf.setLength(0);
                }
                chunks.add(remain.substring(0, TARGET_MAX));
                remain = remain.substring(TARGET_MAX);
            }
            // 放不下就冲刷，并用上一块的尾部作为重叠前缀
            if (buf.length() > 0 && buf.length() + remain.length() > TARGET_MAX) {
                String previous = buf.toString();
                chunks.add(previous);
                buf.setLength(0);
                buf.append(tail(previous, OVERLAP));
            }
            buf.append(remain);
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString());
        }
        return chunks;
    }

    /**
     * 按句末标点与换行切句，标点保留在句子内。
     *
     * @param text 原文
     * @return 句子列表
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；'
                    || c == '!' || c == '?' || c == ';' || c == '\n') {
                sentences.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            sentences.add(text.substring(start));
        }
        return sentences;
    }

    /**
     * 取字符串末尾 n 个字符作为重叠前缀。
     *
     * @param s 原串
     * @param n 期望长度
     * @return 尾部片段
     */
    private String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
