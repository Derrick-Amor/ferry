package seb.service;

import seb.dto.CartUpdateDTO;

import java.util.List;
import java.util.Map;

/**
 * 购物车业务接口。
 */
public interface CartService {

    /**
     * 查询当前用户的购物车，含课程名称、价格、封面等冗余展示字段。
     *
     * @param userId 用户ID
     * @return 购物车行列表，每行为 Map 形式的展示数据
     */
    List<Map<String, Object>> list(Long userId);

    /**
     * 加入购物车；已存在则数量 +1。
     *
     * @param userId   用户ID
     * @param courseId 课程ID
     */
    void add(Long userId, Long courseId);

    /**
     * 修改购物车项的数量或勾选状态。
     *
     * @param userId 用户ID
     * @param id     购物车项ID
     * @param dto    待更新字段
     */
    void update(Long userId, Long id, CartUpdateDTO dto);

    /**
     * 删除购物车项。
     *
     * @param userId 用户ID
     * @param id     购物车项ID
     */
    void delete(Long userId, Long id);
}
