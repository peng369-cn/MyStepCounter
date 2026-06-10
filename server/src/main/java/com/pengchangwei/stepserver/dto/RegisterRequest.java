package com.pengchangwei.stepserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    /** 用户名，长度3-50，全局唯一 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度3-50位")
    private String username;

    /** 密码，长度至少6位，入库前会BCrypt加密 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度6-100位")
    private String password;

    /** 昵称，选填，排行榜展示用 */
    @Size(max = 50, message = "昵称最长50位")
    private String nickname;
}
