package com.pengchangwei.stepserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    /** 当前密码，用于验证身份 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码，长度至少6位 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度6-100位")
    private String newPassword;
}
