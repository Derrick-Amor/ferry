package seb.ai.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import seb.entity.Job;
import seb.entity.KnowledgeDoc;
import seb.mapper.JobMapper;
import seb.mapper.KnowledgeDocMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 岗位知识同步：把 {@code job} 表里在架的职位写成可检索的知识文档。
 *
 * <p>为什么不直接拿数据库字段做检索：{@code job} 表的字段是给结构化查询用的
 * （按城市筛、按薪资排序），而用户问的是自然语言（「杭州有没有不加班的后端岗」）。
 * 把一条职位拼成一段带标题的正文再向量化，才能被语义检索命中；
 * 引用时又能顺着 {@code knowledge_doc.job_id} 跳回岗位详情，两头都不丢。
 *
 * <p>同步只负责 MySQL 这一段，向量化交给 {@link KnowledgeRebuildJob}：
 * 24 条职位逐条调 embedding 要十几秒，放在 HTTP 请求里会被 Nginx 超时截断。
 */
@Service
public class JobKnowledgeSyncService {

    private static final Logger log = LoggerFactory.getLogger(JobKnowledgeSyncService.class);

    /** 职位来源类型 */
    private static final int SOURCE_TYPE_JOB = 2;

    /** 在架状态 */
    private static final int JOB_ONLINE = 1;

    @Resource
    private JobMapper jobMapper;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    /**
     * 同步在架职位。
     *
     * <p>三类处理：
     * <ul>
     *   <li>新职位 → 插入 knowledge_doc；</li>
     *   <li>内容变了（SHA-256 不同）→ 更新正文与哈希；</li>
     *   <li>已下架/删除的职位 → 把对应文档置为禁用（不删，保留向量，
     *       重新上架时改回启用即可，也避免误删历史引用）。</li>
     * </ul>
     *
     * @return 同步统计
     */
    public SyncResult sync() {
        List<Job> jobs = jobMapper.selectList(
                new LambdaQueryWrapper<Job>().eq(Job::getStatus, JOB_ONLINE).orderByAsc(Job::getId));

        List<KnowledgeDoc> existing = knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>().eq(KnowledgeDoc::getSourceType, SOURCE_TYPE_JOB));
        Set<Long> onlineJobIds = new HashSet<>();
        int created = 0, updated = 0, skipped = 0, disabled = 0;

        for (Job job : jobs) {
            onlineJobIds.add(job.getId());
            String content = buildContent(job);
            String hash = DigestUtil.sha256Hex(content);
            KnowledgeDoc doc = existing.stream()
                    .filter(d -> job.getId().equals(d.getJobId()))
                    .findFirst().orElse(null);

            if (doc == null) {
                KnowledgeDoc row = new KnowledgeDoc();
                row.setTitle(buildTitle(job));
                row.setContent(content);
                row.setSourceType(SOURCE_TYPE_JOB);
                row.setJobId(job.getId());
                row.setContentHash(hash);
                row.setChunkCount(0);
                row.setStatus(1);
                knowledgeDocMapper.insert(row);
                created++;
            } else if (!hash.equals(doc.getContentHash()) || doc.getStatus() == null || doc.getStatus() != 1) {
                doc.setTitle(buildTitle(job));
                doc.setContent(content);
                doc.setContentHash(hash);
                doc.setStatus(1);
                knowledgeDocMapper.updateById(doc);
                updated++;
            } else {
                skipped++;
            }
        }

        // 已下架或已删除的职位：禁用文档，避免旧岗位继续被引用
        for (KnowledgeDoc doc : existing) {
            if (doc.getJobId() != null && !onlineJobIds.contains(doc.getJobId())
                    && doc.getStatus() != null && doc.getStatus() == 1) {
                knowledgeDocMapper.update(null, new LambdaUpdateWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getId, doc.getId())
                        .set(KnowledgeDoc::getStatus, 0));
                disabled++;
            }
        }

        SyncResult result = new SyncResult(created, updated, skipped, disabled, jobs.size());
        log.info("[job-sync] 在架职位 {} 条：新增 {}，更新 {}，未变 {}，下架禁用 {}", jobs.size(), created, updated, skipped, disabled);
        return result;
    }

    /**
     * 拼一条职位的检索正文。
     *
     * <p>先列结构化字段，再贴原始描述：字段决定「能不能被条件命中」
     * （城市、薪资、学历都是用户会直接问的词），描述决定「能不能被语义命中」。
     *
     * @param job 职位
     * @return Markdown 正文
     */
    private String buildContent(Job job) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("## 基本信息\n");
        sb.append("- 公司：").append(nullToDash(job.getCompanyName())).append('\n');
        sb.append("- 岗位：").append(nullToDash(job.getJobName())).append('\n');
        sb.append("- 城市：").append(nullToDash(job.getCity())).append('\n');
        sb.append("- 薪资：").append(formatSalary(job.getSalaryMin(), job.getSalaryMax())).append('\n');
        sb.append("- 学历：").append(nullToDash(job.getEducation())).append('\n');
        sb.append("- 经验：").append(nullToDash(job.getExperience())).append('\n');
        sb.append("- 方向：").append(nullToDash(job.getJobCategory())).append("\n\n");
        sb.append("## 岗位描述\n").append(nullToDash(job.getDescription())).append('\n');
        return sb.toString();
    }

    /**
     * 引用卡片上的标题：公司在前，岗位在后。
     *
     * @param job 职位
     * @return 形如「星海科技有限公司 · Java 后端开发工程师」
     */
    private String buildTitle(Job job) {
        String company = job.getCompanyName() == null ? "" : job.getCompanyName().trim();
        String name = job.getJobName() == null ? "" : job.getJobName().trim();
        String title = company.isEmpty() ? name : company + " · " + name;
        return title.length() <= 200 ? title : title.substring(0, 200);
    }

    /**
     * 薪资格式化。字段可能为空，缺一个就只写另一个。
     *
     * @param min 下限
     * @param max 上限
     * @return 形如「15000-25000 元/月」；都为空时返回「面议」
     */
    private String formatSalary(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return "面议";
        }
        if (min == null) {
            return max.stripTrailingZeros().toPlainString() + " 元/月以内";
        }
        if (max == null) {
            return min.stripTrailingZeros().toPlainString() + " 元/月起";
        }
        return min.stripTrailingZeros().toPlainString() + "-"
                + max.stripTrailingZeros().toPlainString() + " 元/月";
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "未填写" : value.trim();
    }

    /**
     * 同步结果。
     *
     * @param created 新增文档数
     * @param updated 更新文档数
     * @param skipped 未变化数
     * @param disabled 因下架而禁用的文档数
     * @param jobTotal 在架职位总数
     */
    public record SyncResult(int created, int updated, int skipped, int disabled, int jobTotal) {

        /**
         * 是否有需要重建索引的变更。
         *
         * @return true 表示新增或更新过文档
         */
        public boolean hasChanges() {
            return created + updated > 0;
        }
    }
}
