package seb.service;

import seb.common.PageResult;
import seb.entity.Orders;

import java.util.List;
import java.util.Map;

/**
 * 订单业务接口：下单、支付、取消与查询。
 */
public interface OrderService {

    /**
     * 从购物车创建订单：写入订单与明细，并清空对应购物车项。
     *
     * @param userId  用户ID
     * @param cartIds 要结算的购物车项ID
     * @return 新订单ID
     */
    Long create(Long userId, List<Long> cartIds);

    /**
     * 分页查询当前用户的订单。
     *
     * @param userId 用户ID
     * @param status 订单状态，null 表示全部
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<Orders> list(Long userId, Integer status, long page, long size);

    /**
     * 订单详情，含明细。
     *
     * @param userId 用户ID
     * @param id     订单ID
     * @return 含 {@code order} / {@code items} 的 Map
     */
    Map<String, Object> detail(Long userId, Long id);

    /**
     * 支付订单，并累加课程报名人数。
     *
     * @param userId 用户ID
     * @param id     订单ID
     */
    void pay(Long userId, Long id);

    /**
     * 取消待支付订单。
     *
     * @param userId 用户ID
     * @param id     订单ID
     */
    void cancel(Long userId, Long id);
}
