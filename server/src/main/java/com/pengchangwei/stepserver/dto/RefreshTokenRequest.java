package com.pengchangwei.stepserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {

    /** 过期的accessToken之后的refreshToken，用来换新的双Token */
    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
