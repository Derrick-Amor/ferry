package seb.controller;

import seb.auth.UserContext;
import seb.common.PageResult;
import seb.common.Result;
import seb.dto.AuditDTO;
import seb.entity.Apply;
import seb.service.ApplyService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/api/apply")
public class ApplyController {

    @Resource
    private ApplyService applyService;

    @GetMapping("/my")
    public Result<PageResult<Apply>> my(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size) {
        return Result.ok(applyService.myList(UserContext.getUserId(), page, size));
    }

    @GetMapping("/pending")
    public Result<PageResult<Apply>> pending(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(applyService.pendingList(page, size));
    }

    @GetMapping("/audited")
    public Result<PageResult<Apply>> audited(@RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(applyService.auditedList(status, page, size));
    }

    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody AuditDTO dto) {
        applyService.audit(dto);
        return Result.ok();
    }
}
