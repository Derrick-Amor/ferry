package seb.service;

import seb.common.PageResult;
import seb.dto.CourseApplyDTO;
import seb.dto.CourseQuery;
import seb.dto.StudyProgressDTO;
import seb.entity.Course;

import java.util.List;
import java.util.Map;

/**
 * 课程业务接口：浏览、收藏、学习进度与上下架申请。
 */
public interface CourseService {

    /**
     * 分页查询已上架课程，支持按类型、技术体系、方向、等级、免费与否筛选与排序。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Course> pageQuery(CourseQuery query);

    /**
     * 课程详情，含章节列表与当前用户是否已可学习。
     *
     * @param id 课程ID
     * @return 含 {@code course} / {@code chapters} / {@code purchased} 的 Map
     */
    Map<String, Object> detail(Long id);

    /**
     * 收藏 / 取消收藏（切换式）。
     *
     * @param userId   用户ID
     * @param courseId 课程ID
     */
    void collect(Long userId, Long courseId);

    /**
     * 进入学习页所需的课程、章节与进度信息。
     *
     * @param userId   用户ID
     * @param courseId 课程ID
     * @return 含 {@code course} / {@code chapters} / {@code progress} / {@code chapterId} 的 Map
     */
    Map<String, Object> study(Long userId, Long courseId);

    /**
     * 查询当前用户已购买的课程列表。
     *
     * @param userId 用户ID
     * @return 课程列表
     */
    List<Course> myCourses(Long userId);

    /**
     * 上报学习进度（不存在则新建；免费课首次学习会累加报名人数）。
     *
     * @param userId 用户ID
     * @param dto    进度参数
     */
    void saveProgress(Long userId, StudyProgressDTO dto);

    /**
     * 讲师申请上架课程：落库课程与章节，并生成一条待审核申请。
     *
     * @param userId 申请人（讲师）ID
     * @param dto    课程信息与章节列表
     */
    void applyList(Long userId, CourseApplyDTO dto);

    /**
     * 讲师申请下架课程：仅生成一条待审核申请，审核通过后才真正改状态。
     *
     * @param userId   申请人ID
     * @param courseId 课程ID
     * @param reason   下架理由
     */
    void offline(Long userId, Long courseId, String reason);
}
