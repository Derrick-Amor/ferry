package seb.controller;

import seb.auth.UserContext;
import seb.common.PageResult;
import seb.common.Result;
import seb.dto.CourseApplyDTO;
import seb.dto.CourseQuery;
import seb.dto.StudyProgressDTO;
import seb.entity.Course;
import seb.service.CourseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course")
public class CourseController {

    @Resource
    private CourseService courseService;

    @GetMapping("/list")
    public Result<PageResult<Course>> list(CourseQuery query) {
        return Result.ok(courseService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(courseService.detail(id));
    }

    @PostMapping("/collect/{id}")
    public Result<Void> collect(@PathVariable Long id) {
        courseService.collect(UserContext.getUserId(), id);
        return Result.ok();
    }

    @GetMapping("/study/{id}")
    public Result<Map<String, Object>> study(@PathVariable Long id) {
        return Result.ok(courseService.study(UserContext.getUserId(), id));
    }

    @GetMapping("/mine")
    public Result<List<Course>> mine() {
        return Result.ok(courseService.myCourses(UserContext.getUserId()));
    }

    @PostMapping("/progress")
    public Result<Void> saveProgress(@RequestBody StudyProgressDTO dto) {
        courseService.saveProgress(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @PostMapping("/applyList")
    public Result<Void> applyList(@Validated @RequestBody CourseApplyDTO dto) {
        courseService.applyList(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @PostMapping("/offline")
    public Result<Void> offline(@RequestParam Long courseId, @RequestParam(required = false) String reason) {
        courseService.offline(UserContext.getUserId(), courseId, reason);
        return Result.ok();
    }
}
