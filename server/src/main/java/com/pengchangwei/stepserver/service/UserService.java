package com.pengchangwei.stepserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pengchangwei.stepserver.common.BusinessException;
import com.pengchangwei.stepserver.common.ErrorCode;
import com.pengchangwei.stepserver.dto.LoginRequest;
import com.pengchangwei.stepserver.dto.RefreshTokenRequest;
import com.pengchangwei.stepserver.dto.RegisterRequest;
import com.pengchangwei.stepserver.entity.User;
import com.pengchangwei.stepserver.mapper.StepRecordMapper;
import com.pengchangwei.stepserver.mapper.UserMapper;
import com.pengchangwei.stepserver.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StepRecordMapper stepRecordMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper userMapper, JwtUtil jwtUtil, StepRecordMapper stepRecordMapper) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.stepRecordMapper = stepRecordMapper;
    }

    /**
     * 注册：先查用户名有没有被占用，没有就BCrypt加密密码后入库，
     * 返回JWT双Token，注册完直接算登录。
     */
    public Map<String, Object> register(RegisterRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setTokenVersion(0);
        userMapper.insert(user);

        return buildTokenData(user);
    }

    /**
     * 登录：拿用户名查用户，比对BCrypt密文，匹配成功签发JWT双Token返回。
     */
    public Map<String, Object> login(LoginRequest request) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(wrapper);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        return buildTokenData(user);
    }

    /**
     * 用refreshToken换取新的双Token（滑动过期）。
     * refreshToken过期或无效直接抛异常，前端收到后跳转登录页。
     */
    public Map<String, Object> refresh(RefreshTokenRequest request) {
        String rtPreview = request.getRefreshToken().length() > 8
                ? request.getRefreshToken().substring(0, 8) : request.getRefreshToken();
        log.info("收到刷新请求, refreshToken前8位={}", rtPreview);
        try {
            Long userId = jwtUtil.getUserIdFromToken(request.getRefreshToken());
            log.info("解析refreshToken成功, userId={}", userId);

            User user = userMapper.selectById(userId);
            if (user == null) {
                log.warn("刷新失败: userId={} 用户不存在", userId);
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }

            int currentVersion = user.getTokenVersion();
            boolean valid = jwtUtil.validateRefreshToken(request.getRefreshToken(), currentVersion);
            log.info("validateRefreshToken结果: valid={}, DBtokenVersion={}", valid, currentVersion);

            if (!valid) {
                log.warn("刷新失败: userId={}, tokenVersion校验不通过(DB={})", userId, currentVersion);
                throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
            }

            Map<String, Object> result = buildTokenData(user);
            log.info("刷新成功, userId={}, 新双Token已签发", userId);
            return result;
        } catch (BusinessException e) {
            log.warn("刷新业务异常: code={}, msg={}", e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("刷新未知异常: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
    }

    /**
     * 修改密码：先验旧密码是否正确，再BCrypt加密新密码后更新入库，
     * 同时token_version+1使所有设备的旧token失效。
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null || !passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userMapper.updateById(user);
    }

    /**
     * 个人中心：返回用户基本信息和累计总步数、总里程。
     */
    public Map<String, Object> getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        int totalSteps = stepRecordMapper.sumStepsByUserId(userId);
        double totalDistance = Math.round(totalSteps * 0.7 / 1000.0 * 100.0) / 100.0;

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("totalSteps", totalSteps);
        data.put("totalDistance", totalDistance);
        return data;
    }

    private Map<String, Object> buildTokenData(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("nickname", user.getNickname());
        data.put("accessToken", jwtUtil.generateAccessToken(user.getId(), user.getTokenVersion()));
        data.put("refreshToken", jwtUtil.generateRefreshToken(user.getId(), user.getTokenVersion()));
        return data;
    }
}
