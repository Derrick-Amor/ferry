package seb.service;

import seb.common.PageResult;
import seb.entity.Course;
import seb.entity.Job;
import seb.entity.Orders;
import seb.vo.UserVO;

import java.util.Map;

/**
 * 后台管理业务接口：用户、订单、课程、职位的管理与状态变更。
 */
public interface AdminService {

    /**
     * 分页查询用户，支持按用户名/昵称关键词、角色、状态筛选。
     *
     * @param keyword 用户名或昵称关键词
     * @param role    角色，null 表示全部
     * @param status  状态，null 表示全部
     * @param page    页码
     * @param size    每页条数
     * @return 用户脱敏信息的分页结果
     */
    PageResult<UserVO> userList(String keyword, Integer role, Integer status, long page, long size);

    /**
     * 启用 / 禁用用户。
     *
     * @param id     用户ID
     * @param status 0禁用 1正常
     */
    void userStatus(Long id, Integer status);

    /**
     * 分页查询订单。
     *
     * @param status  订单状态，null 表示全部
     * @param keyword 订单号关键词
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果
     */
    PageResult<Orders> orderList(Integer status, String keyword, long page, long size);

    /**
     * 订单详情，含明细。
     *
     * @param id 订单ID
     * @return 含 {@code order} / {@code items} 的 Map
     */
    Map<String, Object> orderDetail(Long id);

    /**
     * 直接变更课程上下架状态（绕过审核流程）。
     *
     * @param id     课程ID
     * @param status 目标状态
     */
    void courseStatus(Long id, Integer status);

    /**
     * 直接变更职位上下架状态（绕过审核流程）。
     *
     * @param id     职位ID
     * @param status 目标状态
     */
    void jobStatus(Long id, Integer status);

    /**
     * 分页查询课程。
     *
     * @param status  课程状态，null 表示全部
     * @param keyword 课程名称关键词
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果
     */
    PageResult<Course> courseList(Integer status, String keyword, long page, long size);

    /**
     * 分页查询职位。
     *
     * @param status  职位状态，null 表示全部
     * @param keyword 岗位名称关键词
     * @param page    页码
     * @param size    每页条数
     * @return 分页结果
     */
    PageResult<Job> jobList(Integer status, String keyword, long page, long size);
}
