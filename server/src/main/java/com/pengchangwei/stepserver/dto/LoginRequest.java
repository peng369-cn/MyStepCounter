package com.pengchangwei.stepserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    /** 登录用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码，明文传输，后端拿BCrypt比对 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
