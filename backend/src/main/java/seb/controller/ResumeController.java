package seb.controller;

import seb.auth.UserContext;
import seb.common.Result;
import seb.entity.Resume;
import seb.service.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    @Resource
    private ResumeService resumeService;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(resumeService.upload(UserContext.getUserId(), file));
    }

    @GetMapping("/list")
    public Result<List<Resume>> list() {
        return Result.ok(resumeService.list(UserContext.getUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        resumeService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }
}
