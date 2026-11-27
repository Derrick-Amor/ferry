package seb.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import seb.common.BusinessException;
import seb.common.PageResult;
import seb.dto.AiInterviewSubmitDTO;
import seb.dto.JobApplyDTO;
import seb.dto.JobQuery;
import seb.entity.AiInterview;
import seb.entity.Apply;
import seb.entity.Job;
import seb.entity.JobApply;
import seb.entity.JobCollect;
import seb.entity.Resume;
import seb.entity.User;
import seb.mapper.AiInterviewMapper;
import seb.mapper.ApplyMapper;
import seb.mapper.JobApplyMapper;
import seb.mapper.JobCollectMapper;
import seb.mapper.JobMapper;
import seb.mapper.ResumeMapper;
import seb.mapper.UserMapper;
import seb.service.JobService;

import jakarta.annotation.Resource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link JobService} 的实现。
 */
@Service
public class JobServiceImpl extends ServiceImpl<JobMapper, Job> implements JobService {

    @Resource private JobCollectMapper jobCollectMapper;
    @Resource private JobApplyMapper jobApplyMapper;
    @Resource private AiInterviewMapper aiInterviewMapper;
    @Resource private ApplyMapper applyMapper;
    @Resource private UserMapper userMapper;
    @Resource private ResumeMapper resumeMapper;

    @Override
    public PageResult<Job> pageQuery(JobQuery query) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getStatus, 1);
        if (StrUtil.isNotBlank(query.getJobCategory())) wrapper.eq(Job::getJobCategory, query.getJobCategory());
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(Job::getJobName, query.getKeyword())
                    .or().like(Job::getCompanyName, query.getKeyword()));
        }
        wrapper.orderByDesc(Job::getListTime);
        Page<Job> page = this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page);
    }

    @Override
    public Job detail(Long id) {
        Job job = this.getById(id);
        if (job == null || job.getStatus() != 1) throw new BusinessException("职位不存在或已下架");
        return job;
    }

    @Override
    public void collect(Long userId, Long jobId) {
        JobCollect exist = jobCollectMapper.selectOne(
                new LambdaQueryWrapper<JobCollect>()
                        .eq(JobCollect::getUserId, userId)
                        .eq(JobCollect::getJobId, jobId));
        if (exist != null) {
            jobCollectMapper.deleteById(exist.getId());
        } else {
            JobCollect c = new JobCollect();
            c.setUserId(userId);
            c.setJobId(jobId);
            jobCollectMapper.insert(c);
        }
    }

    @Override
    public void apply(Long userId, Long jobId, Long resumeId) {
        Job job = this.getById(jobId);
        if (job == null || job.getStatus() != 1) throw new BusinessException("职位不存在或已下架");
        JobApply ja = new JobApply();
        ja.setUserId(userId);
        ja.setJobId(jobId);
        ja.setResumeId(resumeId);
        ja.setStatus(0);
        ja.setApplyTime(LocalDateTime.now());
        jobApplyMapper.insert(ja);
        job.setApplyCount(job.getApplyCount() == null ? 1 : job.getApplyCount() + 1);
        this.updateById(job);
    }

    @Override
    public Long aiInterview(Long userId, Long jobId) {
        Job job = this.getById(jobId);
        if (job == null || job.getStatus() != 1) throw new BusinessException("职位不存在或已下架");
        AiInterview ai = new AiInterview();
        ai.setUserId(userId);
        ai.setJobId(jobId);
        ai.setStatus(0);
        aiInterviewMapper.insert(ai);
        return ai.getId();
    }

    @Override
    public void submitInterview(Long userId, AiInterviewSubmitDTO dto) {
        AiInterview ai = aiInterviewMapper.selectById(dto.getInterviewId());
        if (ai == null) throw new BusinessException("面试不存在");
        if (!ai.getUserId().equals(userId)) throw new BusinessException("无权操作该面试");
        if (ai.getStatus() != null && ai.getStatus() == 1) throw new BusinessException("该面试已提交");
        List<String> answers = dto.getAnswers();
        int total = answers == null ? 0 : answers.size();
        int answered = 0;
        StringBuilder sb = new StringBuilder();
        if (answers != null) {
            for (String a : answers) {
                if (StrUtil.isNotBlank(a)) answered++;
                if (sb.length() > 0) sb.append("||");
                sb.append(a == null ? "" : a);
            }
        }
        ai.setResult(sb.toString());
        ai.setScore(total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(Math.min(100, (int) Math.round(answered * 100.0 / total))));
        ai.setStatus(1);
        aiInterviewMapper.updateById(ai);
    }

    @Override
    public AiInterview getInterview(Long userId, Long id) {
        AiInterview ai = aiInterviewMapper.selectById(id);
        if (ai == null) throw new BusinessException("面试不存在");
        if (!ai.getUserId().equals(userId)) throw new BusinessException("无权查看该面试");
        return ai;
    }

    @Override
    public void applyList(Long userId, JobApplyDTO dto) {
        Job job = new Job();
        job.setJobName(dto.getJobName());
        job.setCompanyId(dto.getCompanyId());
        job.setCompanyName(dto.getCompanyName());
        job.setJobCategory(dto.getJobCategory());
        job.setSalaryMin(dto.getSalaryMin());
        job.setSalaryMax(dto.getSalaryMax());
        job.setCity(dto.getCity());
        job.setEducation(dto.getEducation());
        job.setExperience(dto.getExperience());
        job.setDescription(mergeDescription(dto.getDescription(), dto.getDuties()));
        job.setApplyCount(0);
        job.setCollectCount(0);
        job.setStatus(0);
        this.save(job);

        Apply apply = new Apply();
        apply.setApplyType(2);
        apply.setTargetId(job.getId());
        apply.setApplicantId(userId);
        apply.setReason(dto.getReason());
        apply.setStatus(0);
        applyMapper.insert(apply);
    }

    /**
     * 把结构化的岗位职责拼进职位描述文本，供前端直接渲染。
     *
     * @param description 原始描述
     * @param duties      职责条目
     * @return 合并后的描述文本
     */
    private String mergeDescription(String description, List<String> duties) {
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(description)) sb.append(description);
        if (duties != null) {
            for (String d : duties) {
                if (StrUtil.isBlank(d)) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append("• ").append(d);
            }
        }
        return sb.toString();
    }

    @Override
    public List<Job> myJobs(Long userId) {
        List<Apply> applies = applyMapper.selectList(
                new LambdaQueryWrapper<Apply>()
                        .eq(Apply::getApplyType, 2)
                        .eq(Apply::getApplicantId, userId));
        if (applies == null || applies.isEmpty()) return new ArrayList<>();
        List<Long> jobIds = applies.stream().map(Apply::getTargetId)
                .distinct().collect(Collectors.toList());
        return this.listByIds(jobIds);
    }

    @Override
    public List<Map<String, Object>> myApplicants(Long userId, Long jobId) {
        Long cnt = applyMapper.selectCount(
                new LambdaQueryWrapper<Apply>()
                        .eq(Apply::getApplyType, 2)
                        .eq(Apply::getApplicantId, userId)
                        .eq(Apply::getTargetId, jobId));
        if (cnt == null || cnt == 0) throw new BusinessException("无权查看该职位");

        List<JobApply> applies = jobApplyMapper.selectList(
                new LambdaQueryWrapper<JobApply>()
                        .eq(JobApply::getJobId, jobId)
                        .orderByDesc(JobApply::getApplyTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JobApply ja : applies) {
            Map<String, Object> m = new HashMap<>();
            m.put("applyId", ja.getId());
            m.put("userId", ja.getUserId());
            User u = userMapper.selectById(ja.getUserId());
            m.put("nickname", u != null ? u.getNickname() : null);
            m.put("username", u != null ? u.getUsername() : null);
            m.put("phone", u != null ? u.getPhone() : null);
            m.put("email", u != null ? u.getEmail() : null);
            m.put("applyStatus", ja.getStatus());
            m.put("applyTime", ja.getApplyTime());
            if (ja.getResumeId() != null) {
                Resume r = resumeMapper.selectById(ja.getResumeId());
                m.put("resumeFileName", r != null ? r.getFileName() : null);
                m.put("resumeFileUrl", r != null ? r.getFileUrl() : null);
            }
            AiInterview ai = aiInterviewMapper.selectOne(
                    new LambdaQueryWrapper<AiInterview>()
                            .eq(AiInterview::getUserId, ja.getUserId())
                            .eq(AiInterview::getJobId, jobId)
                            .orderByDesc(AiInterview::getCreateTime)
                            .last("limit 1"));
            if (ai != null) {
                m.put("interviewStatus", ai.getStatus());
                m.put("interviewScore", ai.getScore());
                m.put("interviewResult", ai.getResult());
            }
            result.add(m);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> myApplications(Long userId) {
        List<JobApply> applies = jobApplyMapper.selectList(
                new LambdaQueryWrapper<JobApply>()
                        .eq(JobApply::getUserId, userId)
                        .orderByDesc(JobApply::getApplyTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JobApply ja : applies) {
            Job job = this.getById(ja.getJobId());
            Map<String, Object> m = new HashMap<>();
            m.put("applyId", ja.getId());
            m.put("applyStatus", ja.getStatus());
            m.put("applyTime", ja.getApplyTime());
            if (job != null) {
                m.put("jobId", job.getId());
                m.put("jobName", job.getJobName());
                m.put("companyName", job.getCompanyName());
                m.put("jobCategory", job.getJobCategory());
                m.put("salaryMin", job.getSalaryMin());
                m.put("salaryMax", job.getSalaryMax());
                m.put("city", job.getCity());
            }
            AiInterview ai = aiInterviewMapper.selectOne(
                    new LambdaQueryWrapper<AiInterview>()
                            .eq(AiInterview::getUserId, userId)
                            .eq(AiInterview::getJobId, ja.getJobId())
                            .orderByDesc(AiInterview::getCreateTime)
                            .last("limit 1"));
            if (ai != null) {
                m.put("interviewStatus", ai.getStatus());
                m.put("interviewScore", ai.getScore());
                m.put("interviewResult", ai.getResult());
            }
            result.add(m);
        }
        return result;
    }

    @Override
    public void updateApplicantStatus(Long userId, Long applyId, Integer status) {
        JobApply ja = jobApplyMapper.selectById(applyId);
        if (ja == null) throw new BusinessException("投递记录不存在");
        Long cnt = applyMapper.selectCount(
                new LambdaQueryWrapper<Apply>()
                        .eq(Apply::getApplyType, 2)
                        .eq(Apply::getApplicantId, userId)
                        .eq(Apply::getTargetId, ja.getJobId()));
        if (cnt == null || cnt == 0) throw new BusinessException("无权操作该投递");
        ja.setStatus(status);
        jobApplyMapper.updateById(ja);
    }

    @Override
    public void offline(Long userId, Long jobId, String reason) {
        Job job = this.getById(jobId);
        if (job == null) throw new BusinessException("职位不存在");
        Apply apply = new Apply();
        apply.setApplyType(4);
        apply.setTargetId(jobId);
        apply.setApplicantId(userId);
        apply.setReason(reason);
        apply.setStatus(0);
        applyMapper.insert(apply);
    }
}
