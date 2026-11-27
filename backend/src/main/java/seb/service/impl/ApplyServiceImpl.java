package seb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seb.auth.UserContext;
import seb.common.BusinessException;
import seb.common.PageResult;
import seb.dto.AuditDTO;
import seb.entity.Apply;
import seb.entity.Course;
import seb.entity.Job;
import seb.mapper.ApplyMapper;
import seb.mapper.CourseMapper;
import seb.mapper.JobMapper;
import seb.service.ApplyService;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;

/**
 * {@link ApplyService} 的实现。
 */
@Service
public class ApplyServiceImpl extends ServiceImpl<ApplyMapper, Apply> implements ApplyService {

    @Resource private CourseMapper courseMapper;
    @Resource private JobMapper jobMapper;

    @Override
    public PageResult<Apply> myList(Long userId, long page, long size) {
        Page<Apply> p = this.page(new Page<>(page, size),
                new LambdaQueryWrapper<Apply>()
                        .eq(Apply::getApplicantId, userId)
                        .orderByDesc(Apply::getCreateTime));
        return PageResult.of(p);
    }

    @Override
    public PageResult<Apply> pendingList(long page, long size) {
        Page<Apply> p = this.page(new Page<>(page, size),
                new LambdaQueryWrapper<Apply>()
                        .eq(Apply::getStatus, 0)
                        .orderByAsc(Apply::getCreateTime));
        return PageResult.of(p);
    }

    @Override
    public PageResult<Apply> auditedList(Integer status, long page, long size) {
        LambdaQueryWrapper<Apply> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Apply::getStatus, status);
        } else {
            wrapper.in(Apply::getStatus, 1, 2);
        }
        wrapper.orderByDesc(Apply::getAuditTime);
        Page<Apply> p = this.page(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    /**
     * 审核：改 apply 记录 + 联动改 course / job 的上下架状态。
     * 两者必须同生共死，否则会出现「申请已通过但课程仍处于待审核」的错位状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(AuditDTO dto) {
        Apply apply = this.getById(dto.getId());
        if (apply == null) throw new BusinessException("申请不存在");
        if (apply.getStatus() != 0) throw new BusinessException("该申请已处理");

        apply.setStatus(Boolean.TRUE.equals(dto.getPass()) ? 1 : 2);
        apply.setAuditorId(UserContext.getUserId());
        apply.setAuditComment(dto.getAuditComment());
        apply.setAuditTime(LocalDateTime.now());
        this.updateById(apply);

        boolean pass = Boolean.TRUE.equals(dto.getPass());
        if (apply.getApplyType() == 1) {
            Course c = new Course();
            c.setId(apply.getTargetId());
            c.setStatus(pass ? 1 : 3);
            if (pass) c.setListTime(LocalDateTime.now());
            courseMapper.updateById(c);
        } else if (apply.getApplyType() == 2) {
            Job j = new Job();
            j.setId(apply.getTargetId());
            j.setStatus(pass ? 1 : 3);
            if (pass) j.setListTime(LocalDateTime.now());
            jobMapper.updateById(j);
        } else if (apply.getApplyType() == 3) {
            Course c = new Course();
            c.setId(apply.getTargetId());
            c.setStatus(pass ? 2 : 1);
            courseMapper.updateById(c);
        } else if (apply.getApplyType() == 4) {
            Job j = new Job();
            j.setId(apply.getTargetId());
            j.setStatus(pass ? 2 : 1);
            jobMapper.updateById(j);
        }
    }
}
