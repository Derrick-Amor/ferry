package seb.ai.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位方向关键词表，从外部文件加载。
 *
 * <p>为什么不在代码里写死：这套映射是要不断调的——每上一个新方向的岗位就得加词，
 * 每加一个宽词就得回头验证它有没有误伤别的问法（「设计」把「芯片设计」吃掉那次的教训）。
 * 写死在常量里意味着每次调词都要重新构建镜像，调不动就不会去调。
 *
 * <p>文件格式见 {@code config/job-category-keywords.json}，数组顺序即优先级：
 * 靠前的先匹配，「数据库」必须排在「数据」前面。
 *
 * <p>加载失败不阻塞启动：打 error 并降级为空表。空表的后果是岗位咨询退化为纯语义检索，
 * 城市/方向不再是硬条件——评测脚本会立刻暴露（命中率会掉），不会静默变坏。
 */
@Component
public class JobCategoryDictionary {

    private static final Logger log = LoggerFactory.getLogger(JobCategoryDictionary.class);

    /** 关键词表路径，支持 classpath: 与 file: 两种前缀 */
    @Value("${ferry.ai.job-keywords:file:./config/job-category-keywords.json}")
    private String location;

    /** 有序映射：关键词 → 分类列表。顺序即优先级 */
    private final Map<String, List<String>> keywords = new LinkedHashMap<>();

    /** 实际加载成功的路径，便于排查「改了文件没生效」 */
    private String loadedFrom;

    /**
     * 启动时加载一次。
     *
     * <p>词典只在启动时读盘：它变动频率很低，而每次提问都读文件既慢又容易在
     * 文件被编辑到一半时读到坏 JSON。改完重启容器即可，不需要重建镜像。
     */
    @PostConstruct
    public void load() {
        Resource resource = resolve(location);
        if (resource == null || !resource.exists()) {
            log.error("[job-keywords] 关键词表不存在，岗位方向过滤将降级为纯语义：{}", location);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            ObjectMapper mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            KeywordFile file = mapper.readValue(in, KeywordFile.class);
            if (file == null || file.keywords() == null) {
                log.error("[job-keywords] 关键词表格式不对（缺少 keywords 数组）：{}", location);
                return;
            }
            for (KeywordEntry entry : file.keywords()) {
                if (entry == null || entry.keyword() == null || entry.keyword().isBlank()) {
                    continue;
                }
                if (entry.categories() == null || entry.categories().isEmpty()) {
                    log.warn("[job-keywords] 关键词「{}」没有配置分类，已跳过", entry.keyword());
                    continue;
                }
                keywords.put(entry.keyword().toLowerCase(), entry.categories());
            }
            loadedFrom = resource.getDescription();
            log.info("[job-keywords] 已加载 {} 条关键词，来源 {}", keywords.size(), loadedFrom);
        } catch (Exception e) {
            log.error("[job-keywords] 关键词表解析失败，降级为纯语义：{}", location, e);
        }
    }

    /**
     * 在文本里找第一个命中的方向词。
     *
     * @param lowerText 已转小写的问句
     * @return 命中的分类列表；没命中返回空列表
     */
    public List<String> match(String lowerText) {
        if (lowerText == null || lowerText.isBlank() || keywords.isEmpty()) {
            return List.of();
        }
        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            if (lowerText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    /**
     * 已加载的关键词条数，供启动日志与测试断言使用。
     *
     * @return 条数
     */
    public int size() {
        return keywords.size();
    }

    /**
     * 实际加载来源。
     *
     * @return 资源描述；未加载成功时为 null
     */
    public String loadedFrom() {
        return loadedFrom;
    }

    /**
     * 把配置里的路径解析成 Resource。
     *
     * @param path 配置值，如 {@code file:/app/config/x.json} 或 {@code classpath:ai/x.json}
     * @return 资源；路径为空时返回 null
     */
    private Resource resolve(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (path.startsWith("classpath:")) {
            return new ClassPathResource(path.substring("classpath:".length()));
        }
        String filePath = path.startsWith("file:") ? path.substring("file:".length()) : path;
        return new FileSystemResource(filePath);
    }

    /**
     * 关键词表文件结构。
     *
     * @param comment  文件说明，仅作文档用途，不参与逻辑
     * @param keywords 关键词条目，顺序即优先级
     */
    record KeywordFile(List<String> comment, List<KeywordEntry> keywords) {
    }

    /**
     * 单条关键词。
     *
     * @param keyword    关键词（大小写不敏感）
     * @param categories 命中的岗位分类
     * @param note       备注，解释为什么这么配，不参与逻辑
     */
    record KeywordEntry(String keyword, List<String> categories, String note) {
    }
}
