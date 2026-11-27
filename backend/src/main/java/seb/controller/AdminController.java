package seb.controller;

import seb.common.PageResult;
import seb.common.Result;
import seb.entity.Course;
import seb.entity.Job;
import seb.entity.Orders;
import seb.service.AdminService;
import seb.vo.UserVO;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private AdminService adminService;

    @GetMapping("/user/list")
    public Result<PageResult<UserVO>> userList(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer role,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.userList(keyword, role, status, page, size));
    }

    @PutMapping("/user/status/{id}")
    public Result<Void> userStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.userStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/order/list")
    public Result<PageResult<Orders>> orderList(@RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.orderList(status, keyword, page, size));
    }

    @GetMapping("/order/{id}")
    public Result<Map<String, Object>> orderDetail(@PathVariable Long id) {
        return Result.ok(adminService.orderDetail(id));
    }

    @PutMapping("/course/status/{id}")
    public Result<Void> courseStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.courseStatus(id, status);
        return Result.ok();
    }

    @PutMapping("/job/status/{id}")
    public Result<Void> jobStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.jobStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/course/list")
    public Result<PageResult<Course>> courseList(@RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.courseList(status, keyword, page, size));
    }

    @GetMapping("/job/list")
    public Result<PageResult<Job>> jobList(@RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.jobList(status, keyword, page, size));
    }
}
