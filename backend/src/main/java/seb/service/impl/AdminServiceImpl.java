package seb.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import seb.common.BusinessException;
import seb.common.PageResult;
import seb.entity.Course;
import seb.entity.Job;
import seb.entity.OrderItem;
import seb.entity.Orders;
import seb.entity.User;
import seb.mapper.CourseMapper;
import seb.mapper.JobMapper;
import seb.mapper.OrderItemMapper;
import seb.mapper.OrdersMapper;
import seb.mapper.UserMapper;
import seb.service.AdminService;
import seb.vo.UserVO;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link AdminService} 的实现。
 *
 * <p>本类只做跨表查询与状态变更，不持有单一实体的通用 CRUD 能力，
 * 因此直接注入 Mapper，不继承 MyBatis-Plus 的 ServiceImpl。
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Resource private UserMapper userMapper;
    @Resource private OrdersMapper ordersMapper;
    @Resource private OrderItemMapper orderItemMapper;
    @Resource private CourseMapper courseMapper;
    @Resource private JobMapper jobMapper;

    @Override
    public PageResult<UserVO> userList(String keyword, Integer role, Integer status, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        if (role != null) wrapper.eq(User::getRole, role);
        if (status != null) wrapper.eq(User::getStatus, status);
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> p = userMapper.selectPage(new Page<>(page, size), wrapper);
        // 出参必须脱敏：直接用 PageResult.of(p) 会把每行用户的 password 密文一起返回
        List<UserVO> list = p.getRecords().stream().map(UserVO::from).collect(Collectors.toList());
        return new PageResult<>(list, p.getTotal());
    }

    @Override
    public void userStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public PageResult<Orders> orderList(Integer status, String keyword, long page, long size) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Orders::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Orders::getOrderNo, keyword);
        wrapper.orderByDesc(Orders::getCreateTime);
        Page<Orders> p = ordersMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    @Override
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

    @Override
    public void courseStatus(Long id, Integer status) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        course.setStatus(status);
        if (status == 1) course.setListTime(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    @Override
    public void jobStatus(Long id, Integer status) {
        Job job = jobMapper.selectById(id);
        if (job == null) throw new BusinessException("职位不存在");
        job.setStatus(status);
        if (status == 1) job.setListTime(LocalDateTime.now());
        jobMapper.updateById(job);
    }

    @Override
    public PageResult<Course> courseList(Integer status, String keyword, long page, long size) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Course::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Course::getCourseName, keyword);
        wrapper.orderByDesc(Course::getCreateTime);
        Page<Course> p = courseMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    @Override
    public PageResult<Job> jobList(Integer status, String keyword, long page, long size) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(Job::getStatus, status);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Job::getJobName, keyword);
        wrapper.orderByDesc(Job::getCreateTime);
        Page<Job> p = jobMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }
}
