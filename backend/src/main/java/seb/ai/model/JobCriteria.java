package seb.ai.model;

import lombok.Data;
import seb.entity.Job;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 从自然语言里解析出来的岗位硬条件。
 *
 * <p>为什么要单独抽出来：城市、方向、经验这类字段是<b>精确条件</b>，
 * 交给向量和重排去“理解”会出错。实测「杭州 后端开发 岗位」对杭州的
 * 运维、大数据岗能打出 0.81 的高分——模型觉得「都在杭州、都算技术岗，挺相关」，
 * 但用户问的是后端，运维岗就是不符合。
 *
 * <p>所以岗位咨询的链路是「结构化过滤 → 语义排序」：
 * 先用硬条件把候选集缩小到真正符合的记录，再让向量与重排决定先讲哪一条。
 * 条件命中却一条记录都没有时，就是「没有符合条件的岗位」，
 * 这比让模型对着不相关的岗位强行解释要诚实得多。
 */
@Data
public class JobCriteria {

    /** 城市，如「杭州」；为空表示不限 */
    private String city;

    /** 岗位方向集合，如「后端开发」；为空表示不限 */
    private Set<String> categories = new LinkedHashSet<>();

    /** 经验要求，如「应届」；为空表示不限 */
    private String experience;

    /** 是否只要实习岗（用户问「实习」时置 true） */
    private boolean internOnly;

    /**
     * 是否解析出了任何硬条件。
     *
     * @return true 表示这是一次「带条件筛选」的提问
     */
    public boolean hasAny() {
        return (city != null && !city.isBlank())
                || !categories.isEmpty()
                || (experience != null && !experience.isBlank())
                || internOnly;
    }

    /**
     * 判断一条职位是否满足全部已解析的条件。
     *
     * @param job 职位
     * @return true 表示符合
     */
    public boolean matches(Job job) {
        if (job == null) {
            return false;
        }
        if (city != null && !city.isBlank() && !city.equals(job.getCity())) {
            return false;
        }
        if (!categories.isEmpty()) {
            String category = job.getJobCategory() == null ? "" : job.getJobCategory();
            boolean hit = categories.stream().anyMatch(category::contains);
            if (!hit) {
                return false;
            }
        }
        if (experience != null && !experience.isBlank()) {
            String exp = job.getExperience() == null ? "" : job.getExperience();
            if (!exp.contains(experience)) {
                return false;
            }
        }
        if (internOnly) {
            String name = job.getJobName() == null ? "" : job.getJobName();
            if (!name.contains("实习")) {
                return false;
            }
        }
        return true;
    }

    /**
     * 便于日志与调试的可读形式。
     *
     * @return 形如「city=杭州, categories=[后端开发], experience=应届」
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "city=%s, categories=%s, experience=%s, internOnly=%s",
                city == null ? "-" : city,
                categories.isEmpty() ? "-" : categories,
                experience == null ? "-" : experience,
                internOnly);
    }
}
