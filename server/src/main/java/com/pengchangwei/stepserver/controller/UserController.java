package com.pengchangwei.stepserver.controller;

import com.pengchangwei.stepserver.common.Result;
import com.pengchangwei.stepserver.dto.LoginRequest;
import com.pengchangwei.stepserver.dto.RefreshTokenRequest;
import com.pengchangwei.stepserver.dto.RegisterRequest;
import com.pengchangwei.stepserver.service.UserService;
import com.pengchangwei.stepserver.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.pengchangwei.stepserver.dto.ChangePasswordRequest;
import com.pengchangwei.stepserver.security.LoginUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public UserController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * 注册接口，不需要token。用户名全局唯一，注册成功直接返回JWT双Token。
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    /**
     * 登录接口，不需要token。用户名密码匹配后返回JWT双Token。
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    /**
     * 刷新Token，用长效refreshToken换新的双Token，不需要旧accessToken。
     * refreshToken过期后只能重新登录。
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(userService.refresh(request));
    }

    /**
     * 修改密码，需要带token。验证旧密码正确后才允许更新。
     */
    @PutMapping("/password")
    public Result<String> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(LoginUser.get(), request.getOldPassword(), request.getNewPassword());
        return Result.ok("密码修改成功");
    }

    /**
     * 全局版本号查询，供运维确认当前token失效阈值。
     */
    @GetMapping("/global-version")
    public Result<Integer> getGlobalVersion() {
        return Result.ok(jwtUtil.getGlobalVersion());
    }

    /**
     * 个人中心：返回当前用户的基本信息、累计步数和里程。
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile() {
        return Result.ok(userService.getProfile(LoginUser.get()));
    }
}
