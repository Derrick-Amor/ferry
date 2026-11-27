package seb.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import seb.common.BusinessException;
import seb.common.PageResult;
import seb.entity.Cart;
import seb.entity.Course;
import seb.entity.OrderItem;
import seb.entity.Orders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import seb.mapper.CartMapper;
import seb.mapper.CourseMapper;
import seb.mapper.OrderItemMapper;
import seb.mapper.OrdersMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService extends ServiceImpl<OrdersMapper, Orders> {

    @Resource private OrderItemMapper orderItemMapper;
    @Resource private CartMapper cartMapper;
    @Resource private CourseMapper courseMapper;

    /**
     * 购物车下单：写 orders -> 循环写 order_item -> 删除 cart，三步必须同生共死。
     * 缺事务时若 order_item 插入失败，会残留一条没有明细的空订单，且购物车项已被清空。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long userId, List<Long> cartIds) {
        if (cartIds == null || cartIds.isEmpty()) throw new BusinessException("请选择要结算的课程");
        List<Cart> carts = cartMapper.selectBatchIds(cartIds);
        if (carts == null || carts.isEmpty()) throw new BusinessException("购物车项不存在");

        BigDecimal total = BigDecimal.ZERO;
        for (Cart c : carts) {
            if (!c.getUserId().equals(userId)) throw new BusinessException("购物车项不属于当前用户");
            Course course = courseMapper.selectById(c.getCourseId());
            if (course == null || course.getStatus() != 1) throw new BusinessException("课程已下架");
            total = total.add(course.getPrice().multiply(BigDecimal.valueOf(c.getQuantity())));
        }

        Orders order = new Orders();
        order.setOrderNo("ORD" + System.currentTimeMillis() + RandomUtil.randomNumbers(6));
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus(0);
        this.save(order);

        for (Cart c : carts) {
            Course course = courseMapper.selectById(c.getCourseId());
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setCourseId(course.getId());
            item.setCourseName(course.getCourseName());
            item.setPrice(course.getPrice());
            item.setQuantity(c.getQuantity());
            orderItemMapper.insert(item);
        }
        cartMapper.deleteBatchIds(cartIds);
        return order.getId();
    }

    public PageResult<Orders> list(Long userId, Integer status, long page, long size) {
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getUserId, userId)
                .orderByDesc(Orders::getCreateTime);
        if (status != null) wrapper.eq(Orders::getStatus, status);
        Page<Orders> p = this.page(new Page<>(page, size), wrapper);
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long userId, Long id) {
        Orders order = this.getById(id);
        if (order == null || !order.getUserId().equals(userId)) throw new BusinessException("订单不存在");
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        Map<String, Object> map = new HashMap<>();
        map.put("order", order);
        map.put("items", items);
        return map;
    }

    /**
     * 支付：改订单状态 + 累加课程报名人数，跨 orders / course 两张表。
     */
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, Long id) {
        Orders order = this.getById(id);
        if (order == null || !order.getUserId().equals(userId)) throw new BusinessException("订单不存在");
        if (order.getStatus() != 0) throw new BusinessException("订单状态不允许支付");
        order.setStatus(1);
        order.setPayTime(LocalDateTime.now());
        this.updateById(order);

        // 支付成功后，为订单内课程累加报名人数
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        for (OrderItem item : items) {
            Course course = courseMapper.selectById(item.getCourseId());
            if (course != null) {
                course.setEnrollCount((course.getEnrollCount() == null ? 0 : course.getEnrollCount()) + 1);
                courseMapper.updateById(course);
            }
        }
    }

    public void cancel(Long userId, Long id) {
        Orders order = this.getById(id);
        if (order == null || !order.getUserId().equals(userId)) throw new BusinessException("订单不存在");
        if (order.getStatus() != 0) throw new BusinessException("仅待支付订单可取消");
        order.setStatus(2);
        this.updateById(order);
    }
}
