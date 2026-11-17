package com.shiwei.auth.controller;

import com.shiwei.auth.dto.CodeLoginForm;
import com.shiwei.auth.dto.PasswordLoginForm;
import com.shiwei.auth.dto.RegisterForm;
import com.shiwei.auth.dto.SendCodeForm;
import com.shiwei.auth.service.AuthService;
import com.shiwei.auth.vo.LoginVO;
import com.shiwei.auth.vo.UserVO;
import com.shiwei.common.context.UserContext;
import com.shiwei.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录注册")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送登录验证码")
    @PostMapping("/code")
    public Result<String> sendCode(@Valid @RequestBody SendCodeForm form) {
        return Result.ok(authService.sendCode(form.getPhone()));
    }

    @Operation(summary = "验证码登录，未注册自动建号")
    @PostMapping("/login/code")
    public Result<LoginVO> loginByCode(@Valid @RequestBody CodeLoginForm form) {
        return Result.ok(authService.loginByCode(form));
    }

    @Operation(summary = "密码登录")
    @PostMapping("/login/password")
    public Result<LoginVO> loginByPassword(@Valid @RequestBody PasswordLoginForm form) {
        return Result.ok(authService.loginByPassword(form));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterForm form) {
        return Result.ok(authService.register(form));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "authorization", required = false) String token) {
        authService.logout(token);
        return Result.ok();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me(UserContext.requireUserId()));
    }
}
