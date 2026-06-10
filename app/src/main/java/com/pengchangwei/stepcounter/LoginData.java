package com.pengchangwei.stepcounter;

/**
 * 登录/注册接口返回的 data 字段，包含用户信息和 JWT 双令牌。
 */
public class LoginData {

    private Long userId;
    private String nickname;
    private String accessToken;
    private String refreshToken;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
