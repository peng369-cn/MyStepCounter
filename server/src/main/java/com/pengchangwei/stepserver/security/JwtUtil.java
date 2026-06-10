package com.pengchangwei.stepserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT令牌的签发与校验，支持双Token机制：短效accessToken用于接口鉴权，长效refreshToken用于续期。
 * 登录/注册时同时下发两个Token，accessToken过期后用refreshToken换新的，无需重新登录。
 *
 * 三级Token失效防护体系（全部在本类内闭环，JwtInterceptor无感知）：
 *
 * 第三级 — 密钥轮换（parseToken）：
 *   签发始终用新密钥，验证先试新密钥、失败后试旧密钥。
 *   旧密钥验证通过的Token仍然合法（轮换前签发），过渡期后删配置即可。
 *   实现：secret-old 配置项 + parseToken 双密钥 try-catch。
 *
 * 第二级 — 全局踢人（validateToken 校验 gv）：
 *   签发时写入 gv claim（来自配置 app.jwt.global-version），
 *   验证时比对 gv 与当前全局版本号，不匹配则拒绝。
 *   运维改配置 +1 并重启，全部用户所有设备 Token 瞬间失效。
 *   gv 为 null 时默认 0，向后兼容升级前的旧 Token。
 *
 * 第一级 — 单用户踢人（validateToken 校验 version）：
 *   签发时写入 version claim（来自 User 表 tokenVersion），
 *   验证时比对 version 与 DB 最新值，改密码 +1 使旧 Token 失效。
 *   version 为 null 时默认 0，向后兼容升级前的旧 Token。
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    /** 旧签名密钥，用于密钥轮换过渡期验证旧Token，配置为空则不启用 */
    private final SecretKey oldSecretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    /** 全局版本号，运维改配置+1重启可踢全部用户下线 */
    private final int globalVersion;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.secret-old:}") String oldSecret,
                   @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs,
                   @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs,
                   @Value("${app.jwt.global-version:0}") int globalVersion) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.oldSecretKey = (oldSecret != null && !oldSecret.isEmpty())
                ? Keys.hmacShaKeyFor(oldSecret.getBytes(StandardCharsets.UTF_8))
                : null;
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.globalVersion = globalVersion;
    }

    /** 签发短效accessToken，用于接口鉴权，默认15分钟过期 */
    public String generateAccessToken(Long userId, int tokenVersion) {
        return buildToken(userId, accessExpirationMs, "access", tokenVersion);
    }

    /** 签发长效refreshToken，用于续期，默认30天过期 */
    public String generateRefreshToken(Long userId, int tokenVersion) {
        return buildToken(userId, refreshExpirationMs, "refresh", tokenVersion);
    }

    /**
     * 构建JWT令牌，写入 subject(用户ID)、type(access/refresh)、
     * version(用户级tokenVersion)、gv(全局版本号)。
     * 始终用新密钥签发。
     */
    private String buildToken(Long userId, long expirationMs, String tokenType, int tokenVersion) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", tokenType)
                .claim("version", tokenVersion)
                .claim("gv", globalVersion)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /** 从令牌中解析出用户ID，accessToken和refreshToken通用 */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从令牌中解析出版本号，version字段为null时默认返回0，
     * 兼容升级前签发的不带version的旧Token。
     */
    public int getVersionFromToken(String token) {
        Claims claims = parseToken(token);
        Integer version = claims.get("version", Integer.class);
        return version != null ? version : 0;
    }

    /** 检查accessToken是否合法且未过期，同时校验版本号 */
    public boolean validateAccessToken(String token, int expectedVersion) {
        return validateToken(token, "access", expectedVersion);
    }

    /** 检查refreshToken是否合法且未过期，同时校验版本号 */
    public boolean validateRefreshToken(String token, int expectedVersion) {
        return validateToken(token, "refresh", expectedVersion);
    }

    /**
     * Token有效性校验：比对type、用户级version、全局gv。
     * version和gv为null时均默认0，保证升级前后Token兼容。
     */
    private boolean validateToken(String token, String expectedType, int expectedVersion) {
        try {
            Claims claims = parseToken(token);
            String type = claims.get("type", String.class);
            Integer version = claims.get("version", Integer.class);
            int ver = version != null ? version : 0;
            Integer gv = claims.get("gv", Integer.class);
            int gvVal = gv != null ? gv : 0;
            return expectedType.equals(type) && ver == expectedVersion && gvVal == globalVersion;
        } catch (Exception e) {
            return false;
        }
    }

    /** 返回当前全局版本号，供运维确认 */
    public int getGlobalVersion() {
        return globalVersion;
    }

    /**
     * 解析Token签名：先拿新密钥验证，失败且oldSecretKey不为空时用旧密钥重试。
     * 旧密钥验证通过说明Token是轮换前签发，过渡期后删除secret-old配置即可。
     */
    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            if (oldSecretKey != null) {
                return Jwts.parser()
                        .verifyWith(oldSecretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            }
            throw e;
        }
    }
}
