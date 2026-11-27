package seb.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import seb.auth.UserContext;
import seb.common.BusinessException;
import seb.common.PageResult;
import seb.dto.CourseApplyDTO;
import seb.dto.CourseQuery;
import seb.dto.StudyProgressDTO;
import seb.entity.*;
import seb.mapper.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CourseService extends ServiceImpl<CourseMapper, Course> {

    @Resource private CourseChapterMapper courseChapterMapper;
    @Resource private CourseCollectMapper courseCollectMapper;
    @Resource private StudyRecordMapper studyRecordMapper;
    @Resource private ApplyMapper applyMapper;
    @Resource private UserMapper userMapper;
    @Resource private OrdersMapper ordersMapper;
    @Resource private OrderItemMapper orderItemMapper;

    public PageResult<Course> pageQuery(CourseQuery query) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getStatus, 1);
        if (query.getCourseType() != null) wrapper.eq(Course::getCourseType, query.getCourseType());
        if (StrUtil.isNotBlank(query.getTechSystem())) wrapper.eq(Course::getTechSystem, query.getTechSystem());
        if (StrUtil.isNotBlank(query.getTechDirection())) wrapper.eq(Course::getTechDirection, query.getTechDirection());
        if (StrUtil.isNotBlank(query.getCourseLevel())) wrapper.eq(Course::getCourseLevel, query.getCourseLevel());
        if (query.getIsFree() != null) wrapper.eq(Course::getIsFree, query.getIsFree());
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(Course::getCourseName, query.getKeyword())
                    .or().like(Course::getTeacherName, query.getKeyword()));
        }
        boolean asc = "asc".equalsIgnoreCase(query.getOrder());
        String sortBy = query.getSortBy();
        if ("enroll".equals(sortBy)) {
            wrapper.orderBy(true, asc, Course::getEnrollCount);
        } else if ("rating".equals(sortBy)) {
            wrapper.orderBy(true, asc, Course::getRating);
        } else {
            wrapper.orderBy(true, asc, Course::getListTime);
        }
        Page<Course> page = this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
        return PageResult.of(page);
    }

    public Map<String, Object> detail(Long id) {
        Course course = this.getById(id);
        if (course == null || course.getStatus() != 1) throw new BusinessException("课程不存在或已下架");
        List<CourseChapter> chapters = courseChapterMapper.selectList(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, id)
                        .orderByAsc(CourseChapter::getSortNo));
        Map<String, Object> map = new HashMap<>();
        map.put("course", course);
        map.put("chapters", chapters);
        map.put("purchased", canStudy(UserContext.getUserId(), course));
        return map;
    }

    public void collect(Long userId, Long courseId) {
        CourseCollect exist = courseCollectMapper.selectOne(
                new LambdaQueryWrapper<CourseCollect>()
                        .eq(CourseCollect::getUserId, userId)
                        .eq(CourseCollect::getCourseId, courseId));
        if (exist != null) {
            courseCollectMapper.deleteById(exist.getId());
        } else {
            CourseCollect c = new CourseCollect();
            c.setUserId(userId);
            c.setCourseId(courseId);
            courseCollectMapper.insert(c);
        }
    }

    public Map<String, Object> study(Long userId, Long courseId) {
        Course course = this.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        if (course.getStatus() == null || course.getStatus() != 1) throw new BusinessException("课程已下架");
        if (!canStudy(userId, course)) throw new BusinessException("请先购买该课程");
        List<CourseChapter> chapters = courseChapterMapper.selectList(
                new LambdaQueryWrapper<CourseChapter>()
                        .eq(CourseChapter::getCourseId, courseId)
                        .orderByAsc(CourseChapter::getSortNo));
        StudyRecord record = studyRecordMapper.selectOne(
                new LambdaQueryWrapper<StudyRecord>()
                        .eq(StudyRecord::getUserId, userId)
                        .eq(StudyRecord::getCourseId, courseId));
        Map<String, Object> map = new HashMap<>();
        map.put("course", course);
        map.put("chapters", chapters);
        map.put("progress", record != null ? record.getProgress() : BigDecimal.ZERO);
        map.put("chapterId", record != null ? record.getChapterId() : null);
        return map;
    }

    public List<Course> myCourses(Long userId) {
        List<Orders> orders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
                        .eq(Orders::getStatus, 1));
        if (orders == null || orders.isEmpty()) return new ArrayList<>();
        List<Long> orderIds = orders.stream().map(Orders::getId).collect(Collectors.toList());
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
        if (items == null || items.isEmpty()) return new ArrayList<>();
        List<Long> courseIds = items.stream().map(OrderItem::getCourseId)
                .distinct().collect(Collectors.toList());
        if (courseIds.isEmpty()) return new ArrayList<>();
        return this.list(new LambdaQueryWrapper<Course>()
                .in(Course::getId, courseIds)
                .eq(Course::getStatus, 1));
    }

    public void saveProgress(Long userId, StudyProgressDTO dto) {
        Course course = this.getById(dto.getCourseId());
        if (course == null) throw new BusinessException("课程不存在");
        StudyRecord record = studyRecordMapper.selectOne(
                new LambdaQueryWrapper<StudyRecord>()
                        .eq(StudyRecord::getUserId, userId)
                        .eq(StudyRecord::getCourseId, dto.getCourseId()));
        if (record == null) {
            record = new StudyRecord();
            record.setUserId(userId);
            record.setCourseId(dto.getCourseId());
        }
        record.setChapterId(dto.getChapterId());
        record.setProgress(dto.getProgress() != null ? dto.getProgress() : BigDecimal.ZERO);
        record.setLastTime(LocalDateTime.now());
        if (record.getId() == null) {
            studyRecordMapper.insert(record);
            if (course.getIsFree() != null && course.getIsFree() == 1) {
                course.setEnrollCount((course.getEnrollCount() == null ? 0 : course.getEnrollCount()) + 1);
                this.updateById(course);
            }
        } else {
            studyRecordMapper.updateById(record);
        }
    }

    public void applyList(Long userId, CourseApplyDTO dto) {
        Course course = new Course();
        course.setCourseName(dto.getCourseName());
        course.setCourseType(dto.getCourseType() != null ? dto.getCourseType() : 1);
        course.setTechSystem(dto.getTechSystem());
        course.setTechDirection(dto.getTechDirection());
        course.setCourseLevel(dto.getCourseLevel());
        course.setIsFree(dto.getIsFree() != null ? dto.getIsFree() : 0);
        course.setPrice(dto.getPrice() != null ? dto.getPrice() : BigDecimal.ZERO);
        course.setCoverImage(dto.getCoverImage());
        course.setDescription(dto.getDescription());
        course.setTeacherId(userId);
        User teacher = userMapper.selectById(userId);
        if (teacher != null) {
            course.setTeacherName(StrUtil.isNotBlank(teacher.getNickname())
                    ? teacher.getNickname() : teacher.getUsername());
        }
        course.setRating(BigDecimal.ZERO);
        course.setEnrollCount(0);
        course.setStatus(0);
        this.save(course);

        if (dto.getChapters() != null) {
            int sortNo = 1;
            for (CourseApplyDTO.ChapterItem ch : dto.getChapters()) {
                if (StrUtil.isBlank(ch.getChapterName())) continue;
                CourseChapter chapter = new CourseChapter();
                chapter.setCourseId(course.getId());
                chapter.setChapterName(ch.getChapterName());
                chapter.setDuration(ch.getDuration() != null ? ch.getDuration() : 0);
                chapter.setVideoUrl(ch.getVideoUrl());
                chapter.setSortNo(sortNo++);
                courseChapterMapper.insert(chapter);
            }
        }

        saveApply(1, course.getId(), userId, dto.getReason());
    }

    public void offline(Long userId, Long courseId, String reason) {
        Course course = this.getById(courseId);
        if (course == null) throw new BusinessException("课程不存在");
        saveApply(3, courseId, userId, reason);
    }

    private boolean canStudy(Long userId, Course course) {
        if (course.getIsFree() != null && course.getIsFree() == 1) return true;
        if (userId == null) return false;
        return hasPurchased(userId, course.getId());
    }

    private boolean hasPurchased(Long userId, Long courseId) {
        List<Orders> orders = ordersMapper.selectList(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, userId)
                        .eq(Orders::getStatus, 1));
        if (orders == null || orders.isEmpty()) return false;
        List<Long> orderIds = orders.stream().map(Orders::getId).collect(Collectors.toList());
        Long count = orderItemMapper.selectCount(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderId, orderIds)
                        .eq(OrderItem::getCourseId, courseId));
        return count != null && count > 0;
    }

    private void saveApply(int type, Long targetId, Long applicantId, String reason) {
        Apply apply = new Apply();
        apply.setApplyType(type);
        apply.setTargetId(targetId);
        apply.setApplicantId(applicantId);
        apply.setReason(reason);
        apply.setStatus(0);
        applyMapper.insert(apply);
    }
}
