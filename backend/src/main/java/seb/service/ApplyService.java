package seb.service;

import seb.common.PageResult;
import seb.dto.AuditDTO;
import seb.entity.Apply;

/**
 * 上下架申请业务接口：申请人查看自己的申请，管理员审核。
 */
public interface ApplyService {

    /**
     * 分页查询当前用户提交的申请。
     *
     * @param userId 申请人ID
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<Apply> myList(Long userId, long page, long size);

    /**
     * 分页查询待审核申请，按下单时间升序（先到先审）。
     *
     * @param page 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<Apply> pendingList(long page, long size);

    /**
     * 分页查询已审核申请。
     *
     * @param status 1通过 2驳回，null 表示两者都要
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<Apply> auditedList(Integer status, long page, long size);

    /**
     * 审核申请，并联动修改课程 / 职位的上下架状态。
     *
     * @param dto 审核参数
     */
    void audit(AuditDTO dto);
}
