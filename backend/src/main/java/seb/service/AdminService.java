package seb.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import seb.common.BusinessException;
import seb.common.PageResult;
import seb.entity.*;
import seb.mapper.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    @Resource private UserMapper userMapper;
    @Resource private OrdersMapper ordersMapper;
    @Resource private OrderItemMapper orderItemMapper;
    @Resource private CourseMapper courseMapper;
    @Resource private JobMapper jobMapper;

    public PageResult<User> userList(String keyword, Integer role, Integer status, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        if (role != null) wrapper.eq(User::getRole, role);
        if (status != null) wrapper.eq(User::getStatus, status);
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> p = userMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    public void userStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public PageResult<Orders> orderList(Integer status, String keyword, long page, long size) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Orders::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Orders::getOrderNo, keyword);
        wrapper.orderByDesc(Orders::getCreateTime);
        Page<Orders> p = ordersMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    public Map<String, Object> orderDetail(Long id) {
        Orders order = ordersMapper.selectById(id);
        if (order == null) throw new BusinessException("订单不存在");
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        map.put("items", items);
        return map;
    }

    public void courseStatus(Long id, Integer status) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        course.setStatus(status);
        if (status == 1) course.setListTime(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    public void jobStatus(Long id, Integer status) {
        Job job = jobMapper.selectById(id);
        if (job == null) throw new BusinessException("职位不存在");
        job.setStatus(status);
        if (status == 1) job.setListTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    public PageResult<Course> courseList(Integer status, String keyword, long page, long size) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Course::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Course::getCourseName, keyword);
        wrapper.orderByDesc(Course::getCreateTime);
        Page<Course> p = courseMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    public PageResult<Job> jobList(Integer status, String keyword, long page, long size) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Job::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Job::getJobName, keyword);
        wrapper.orderByDesc(Job::getCreateTime);
        Page<Job> p = jobMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }
}
