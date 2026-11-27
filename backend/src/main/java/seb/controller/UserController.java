package seb.controller;

import seb.auth.UserContext;
import seb.common.Result;

import seb.entity.User;
import seb.service.UserService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import seb.dto.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @PostMapping("/resetPassword")
    public Result<Void> resetPassword(@Validated @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @GetMapping("/info")
    public Result<User> info() {
        return Result.ok(userService.getInfo(UserContext.getUserId()));
    }

    @PutMapping("/info")
    public Result<Void> updateInfo(@Validated @RequestBody UpdateUserInfoDTO dto) {
        userService.updateInfo(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @PostMapping("/forgotPassword")
    public Result<Void> forgotPassword(@Validated @RequestBody ForgotPasswordDTO dto) {
        userService.forgotPassword(dto);
        return Result.ok();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }
}
