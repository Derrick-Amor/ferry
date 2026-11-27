package seb.controller;

import seb.auth.UserContext;
import seb.common.PageResult;
import seb.common.Result;
import seb.dto.AiInterviewSubmitDTO;
import seb.dto.JobApplyDTO;
import seb.dto.JobQuery;
import seb.entity.AiInterview;
import seb.entity.Job;
import seb.service.JobService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job")
public class JobController {

    @Resource
    private JobService jobService;

    @GetMapping("/list")
    public Result<PageResult<Job>> list(JobQuery query) {
        return Result.ok(jobService.pageQuery(query));
    }

    @GetMapping("/{id}")
    public Result<Job> detail(@PathVariable Long id) {
        return Result.ok(jobService.detail(id));
    }

    @PostMapping("/collect/{id}")
    public Result<Void> collect(@PathVariable Long id) {
        jobService.collect(UserContext.getUserId(), id);
        return Result.ok();
    }

    @PostMapping("/apply/{id}")
    public Result<Void> apply(@PathVariable Long id, @RequestParam(required = false) Long resumeId) {
        jobService.apply(UserContext.getUserId(), id, resumeId);
        return Result.ok();
    }

    @PostMapping("/ai-interview/{id}")
    public Result<Long> aiInterview(@PathVariable Long id) {
        return Result.ok(jobService.aiInterview(UserContext.getUserId(), id));
    }

    @PostMapping("/interview/submit")
    public Result<Void> submitInterview(@RequestBody AiInterviewSubmitDTO dto) {
        jobService.submitInterview(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @GetMapping("/interview/report/{id}")
    public Result<AiInterview> interviewReport(@PathVariable Long id) {
        return Result.ok(jobService.getInterview(UserContext.getUserId(), id));
    }

    @GetMapping("/my")
    public Result<List<Job>> my() {
        return Result.ok(jobService.myJobs(UserContext.getUserId()));
    }

    @GetMapping("/my/applicants/{jobId}")
    public Result<List<Map<String, Object>>> myApplicants(@PathVariable Long jobId) {
        return Result.ok(jobService.myApplicants(UserContext.getUserId(), jobId));
    }

    @GetMapping("/my/applications")
    public Result<List<Map<String, Object>>> myApplications() {
        return Result.ok(jobService.myApplications(UserContext.getUserId()));
    }

    @PutMapping("/my/applicants/{applyId}")
    public Result<Void> updateApplicantStatus(@PathVariable Long applyId, @RequestParam Integer status) {
        jobService.updateApplicantStatus(UserContext.getUserId(), applyId, status);
        return Result.ok();
    }

    @PostMapping("/applyList")
    public Result<Void> applyList(@Validated @RequestBody JobApplyDTO dto) {
        jobService.applyList(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @PostMapping("/offline")
    public Result<Void> offline(@RequestParam Long jobId, @RequestParam(required = false) String reason) {
        jobService.offline(UserContext.getUserId(), jobId, reason);
        return Result.ok();
    }
}
