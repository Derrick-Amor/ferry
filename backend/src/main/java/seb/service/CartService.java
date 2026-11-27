package seb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import seb.common.BusinessException;
import seb.dto.CartUpdateDTO;
import seb.entity.Cart;
import seb.entity.Course;
import seb.mapper.CartMapper;
import seb.mapper.CourseMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CartService extends ServiceImpl<CartMapper, Cart> {

    @Resource private CourseMapper courseMapper;

    public List<Map<String, Object>> list(Long userId) {
        List<Cart> carts = this.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Cart c : carts) {
            Course course = courseMapper.selectById(c.getCourseId());
            Map<String, Object> m = new HashMap<>();
            m.put("cartId", c.getId());
            m.put("courseId", c.getCourseId());
            m.put("quantity", c.getQuantity());
            m.put("checked", c.getChecked());
            if (course != null) {
                m.put("courseName", course.getCourseName());
                m.put("price", course.getPrice());
                m.put("coverImage", course.getCoverImage());
            }
            result.add(m);
        }
        return result;
    }

    public void add(Long userId, Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || course.getStatus() != 1) throw new BusinessException("课程不存在或已下架");
        Cart exist = this.getOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getCourseId, courseId));
        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + 1);
            this.updateById(exist);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setCourseId(courseId);
            cart.setQuantity(1);
            cart.setChecked(1);
            this.save(cart);
        }
    }

    public void update(Long userId, Long id, CartUpdateDTO dto) {
        Cart cart = this.getById(id);
        if (cart == null || !cart.getUserId().equals(userId)) throw new BusinessException("购物车项不存在");
        if (dto.getQuantity() != null) cart.setQuantity(dto.getQuantity());
        if (dto.getChecked() != null) cart.setChecked(dto.getChecked());
        this.updateById(cart);
    }

    public void delete(Long userId, Long id) {
        Cart cart = this.getById(id);
        if (cart == null || !cart.getUserId().equals(userId)) throw new BusinessException("购物车项不存在");
        this.removeById(id);
    }
}
