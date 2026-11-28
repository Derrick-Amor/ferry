package seb.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import seb.ai.model.JobCriteria;
import seb.entity.Job;
import seb.mapper.JobMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 岗位提问的条件解析器：把「杭州有没有后端岗位」翻译成
 * {@code city=杭州, categories=[后端开发]}。
 *
 * <p>为什么不交给大模型解析：这一步要的是<b>确定性</b>，不是创造力。
 * 「杭州」就是杭州，不存在“大概在杭州”这种解释空间；用规则解析既快又不会漂。
 * 大模型留给后面的生成环节，用它做它擅长的事。
 *
 * <p>城市表从 {@code job} 表现查，不写死：新增城市时不用改代码。
 */
@Service
public class JobQueryParser {

    /** 城市列表缓存时长，避免每次提问都查一遍库 */
    private static final long CITY_CACHE_MS = 5 * 60 * 1000L;

    /**
     * 方向关键词 → 岗位分类。
     *
     * <p>顺序有意义：越具体的词排在越前面，命中即停。
     * 例如「数据库」必须排在「数据」之前，否则会被宽泛的「数据」抢走。
     */
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put("数据库", List.of("数据存储"));
        CATEGORY_KEYWORDS.put("dba", List.of("数据存储"));
        CATEGORY_KEYWORDS.put("大数据", List.of("大数据"));
        CATEGORY_KEYWORDS.put("数仓", List.of("大数据"));
        CATEGORY_KEYWORDS.put("数据治理", List.of("数据治理"));
        CATEGORY_KEYWORDS.put("数据分析", List.of("数据分析"));
        CATEGORY_KEYWORDS.put("分析师", List.of("数据分析"));
        CATEGORY_KEYWORDS.put("后端", List.of("后端开发"));
        CATEGORY_KEYWORDS.put("服务端", List.of("后端开发"));
        CATEGORY_KEYWORDS.put("java", List.of("后端开发"));
        CATEGORY_KEYWORDS.put("前端", List.of("前端开发"));
        CATEGORY_KEYWORDS.put("vue", List.of("前端开发"));
        CATEGORY_KEYWORDS.put("react", List.of("前端开发"));
        CATEGORY_KEYWORDS.put("移动端", List.of("移动开发"));
        CATEGORY_KEYWORDS.put("安卓", List.of("移动开发"));
        CATEGORY_KEYWORDS.put("android", List.of("移动开发"));
        CATEGORY_KEYWORDS.put("ios", List.of("移动开发"));
        CATEGORY_KEYWORDS.put("测试", List.of("测试"));
        CATEGORY_KEYWORDS.put("qa", List.of("测试"));
        CATEGORY_KEYWORDS.put("运维", List.of("运维"));
        CATEGORY_KEYWORDS.put("sre", List.of("运维"));
        CATEGORY_KEYWORDS.put("算法", List.of("算法"));
        CATEGORY_KEYWORDS.put("机器学习", List.of("算法"));
        CATEGORY_KEYWORDS.put("全栈", List.of("全栈"));
        CATEGORY_KEYWORDS.put("嵌入式", List.of("嵌入式"));
        // 刻意不写「设计」：它会把「芯片设计」「架构设计」也当成设计岗，
        // 实测就是这一条把「有没有芯片设计相关的职位」从拒答变成了误答
        CATEGORY_KEYWORDS.put("设计师", List.of("设计"));
        CATEGORY_KEYWORDS.put("ui", List.of("设计"));
        CATEGORY_KEYWORDS.put("ux", List.of("设计"));
        CATEGORY_KEYWORDS.put("交互设计", List.of("设计"));
        CATEGORY_KEYWORDS.put("售前", List.of("售前"));
        CATEGORY_KEYWORDS.put("实施", List.of("实施"));
        CATEGORY_KEYWORDS.put("技术支持", List.of("技术支持"));
        CATEGORY_KEYWORDS.put("产品", List.of("产品"));
        CATEGORY_KEYWORDS.put("运营", List.of("运营"));
        CATEGORY_KEYWORDS.put("大模型", List.of("人工智能"));
        CATEGORY_KEYWORDS.put("人工智能", List.of("人工智能"));
        CATEGORY_KEYWORDS.put("ai", List.of("人工智能"));
        // 「数据」放在最后：它最宽泛，前面更具体的词都没命中时才用它
        CATEGORY_KEYWORDS.put("数据", List.of("大数据", "数据分析", "数据存储"));
    }

    @Resource
    private JobMapper jobMapper;

    private volatile List<String> cachedCities = List.of();

    private volatile long cachedAt = 0L;

    /**
     * 解析问题中的岗位硬条件。
     *
     * @param question 用户问题
     * @return 解析结果；没有任何条件时各项为空
     */
    public JobCriteria parse(String question) {
        JobCriteria criteria = new JobCriteria();
        if (question == null || question.isBlank()) {
            return criteria;
        }
        String text = question.toLowerCase(Locale.ROOT);

        for (String city : cities()) {
            if (text.contains(city.toLowerCase(Locale.ROOT))) {
                criteria.setCity(city);
                break;
            }
        }

        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (text.contains(entry.getKey())) {
                criteria.getCategories().addAll(entry.getValue());
                break;
            }
        }

        if (text.contains("实习")) {
            // 「实习」比其他经验词更具体，单独收紧到岗位名含「实习」
            criteria.setInternOnly(true);
        } else if (text.contains("应届") || text.contains("校招") || text.contains("毕业生")) {
            criteria.setExperience("应届");
        }
        return criteria;
    }

    /**
     * 在架岗位涉及的城市列表，带 5 分钟缓存。
     *
     * @return 城市名列表；查不到时返回空列表（此时不做城市过滤）
     */
    private List<String> cities() {
        long now = System.currentTimeMillis();
        if (now - cachedAt < CITY_CACHE_MS && !cachedCities.isEmpty()) {
            return cachedCities;
        }
        List<Job> jobs = jobMapper.selectList(new LambdaQueryWrapper<Job>()
                .select(Job::getCity)
                .eq(Job::getStatus, 1));
        List<String> cities = new ArrayList<>();
        for (Job job : jobs) {
            String city = job.getCity();
            if (city != null && !city.isBlank() && !cities.contains(city)) {
                cities.add(city.trim());
            }
        }
        cachedCities = cities;
        cachedAt = now;
        return cities;
    }
}
