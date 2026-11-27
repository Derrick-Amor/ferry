package seb.service;

import seb.common.PageResult;
import seb.dto.AiInterviewSubmitDTO;
import seb.dto.JobApplyDTO;
import seb.dto.JobQuery;
import seb.entity.AiInterview;
import seb.entity.Job;

import java.util.List;
import java.util.Map;

/**
 * 职位业务接口：浏览、收藏、投递、AI 面试与上下架申请。
 */
public interface JobService {

    /**
     * 分页查询已上架职位。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Job> pageQuery(JobQuery query);

    /**
     * 职位详情。
     *
     * @param id 职位ID
     * @return 职位实体
     */
    Job detail(Long id);

    /**
     * 收藏 / 取消收藏（切换式）。
     *
     * @param userId 用户ID
     * @param jobId  职位ID
     */
    void collect(Long userId, Long jobId);

    /**
     * 投递职位并累加职位申请人数。
     *
     * @param userId   用户ID
     * @param jobId    职位ID
     * @param resumeId 简历ID，可为 null
     */
    void apply(Long userId, Long jobId, Long resumeId);

    /**
     * 发起一场 AI 面试。
     *
     * @param userId 用户ID
     * @param jobId  职位ID
     * @return 面试记录ID
     */
    Long aiInterview(Long userId, Long jobId);

    /**
     * 提交面试作答并计算得分。
     *
     * @param userId 用户ID
     * @param dto    面试ID与作答列表
     */
    void submitInterview(Long userId, AiInterviewSubmitDTO dto);

    /**
     * 查询面试报告。
     *
     * @param userId 用户ID
     * @param id     面试ID
     * @return 面试记录
     */
    AiInterview getInterview(Long userId, Long id);

    /**
     * 企业申请上架职位：落库职位并生成一条待审核申请。
     *
     * @param userId 申请人（企业）ID
     * @param dto    职位信息
     */
    void applyList(Long userId, JobApplyDTO dto);

    /**
     * 查询当前用户（企业）发布的职位。
     *
     * @param userId 用户ID
     * @return 职位列表
     */
    List<Job> myJobs(Long userId);

    /**
     * 查询某职位的候选人列表，含简历与面试结果。
     *
     * @param userId 当前用户（企业）ID
     * @param jobId  职位ID
     * @return 候选人信息列表
     */
    List<Map<String, Object>> myApplicants(Long userId, Long jobId);

    /**
     * 查询当前用户（学生）的全部投递记录。
     *
     * @param userId 用户ID
     * @return 投递记录列表
     */
    List<Map<String, Object>> myApplications(Long userId);

    /**
     * 企业更新候选人投递状态。
     *
     * @param userId  当前用户（企业）ID
     * @param applyId 投递记录ID
     * @param status  目标状态
     */
    void updateApplicantStatus(Long userId, Long applyId, Integer status);

    /**
     * 企业申请下架职位：仅生成一条待审核申请。
     *
     * @param userId 申请人ID
     * @param jobId  职位ID
     * @param reason 下架理由
     */
    void offline(Long userId, Long jobId, String reason);
}
