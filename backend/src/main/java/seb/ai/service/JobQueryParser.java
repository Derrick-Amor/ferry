package seb.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import seb.ai.model.JobCriteria;
import seb.entity.Job;
import seb.mapper.JobMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 岗位提问的条件解析器：把「杭州有没有后端岗位」翻译成
 * {@code city=杭州, categories=[后端开发]}。
 *
 * <p>为什么不交给大模型解析：这一步要的是<b>确定性</b>，不是创造力。
 * 「杭州」就是杭州，不存在“大概在杭州”这种解释空间；用规则解析既快又不会漂。
 * 大模型留给后面的生成环节，用它做它擅长的事。
 *
 * <p>方向词的映射表放在 {@link JobCategoryDictionary}（外部 JSON 文件），
 * 城市表从 {@code job} 表现查并缓存——两者都能跟着数据变，不用改代码。
 */
@Service
public class JobQueryParser {

    /** 城市列表缓存时长，避免每次提问都查一遍库 */
    private static final long CITY_CACHE_MS = 5 * 60 * 1000L;

    @Resource
    private JobMapper jobMapper;

    @Resource
    private JobCategoryDictionary categoryDictionary;

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

        criteria.getCategories().addAll(categoryDictionary.match(text));

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
     * <p>不写死城市名：新增城市的岗位时不用改代码。
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
