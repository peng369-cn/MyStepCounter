package com.pengchangwei.stepserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pengchangwei.stepserver.common.BusinessException;
import com.pengchangwei.stepserver.common.ErrorCode;
import com.pengchangwei.stepserver.dto.LoginRequest;
import com.pengchangwei.stepserver.dto.RegisterRequest;
import com.pengchangwei.stepserver.entity.User;
import com.pengchangwei.stepserver.mapper.StepRecordMapper;
import com.pengchangwei.stepserver.mapper.UserMapper;
import com.pengchangwei.stepserver.security.JwtUtil;
import com.pengchangwei.stepserver.dto.RefreshTokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StepRecordMapper stepRecordMapper;

    private UserService userService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper, jwtUtil, stepRecordMapper);
    }

    /** 注册时用户名已存在 → 抛 USERNAME_EXISTS 异常 */
    @Test
    void register_whenUsernameExists_shouldThrowException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("test");
        request.setPassword("123456");
        request.setNickname("测试");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request));
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), ex.getCode());
    }

    /** 注册成功 → 返回双Token和用户信息 */
    @Test
    void register_whenSuccess_shouldReturnTokenData() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("123456");
        request.setNickname("新人");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return null;
        }).when(userMapper).insert(any(User.class));
        when(jwtUtil.generateAccessToken(anyLong(), anyInt())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyInt())).thenReturn("refresh-token");

        Map<String, Object> result = userService.register(request);

        assertNotNull(result.get("userId"));
        assertEquals("新人", result.get("nickname"));
        assertEquals("access-token", result.get("accessToken"));
        assertEquals("refresh-token", result.get("refreshToken"));
        verify(userMapper).insert(any(User.class));
    }

    /** 登录时用户不存在 → 抛 LOGIN_FAILED */
    @Test
    void login_whenUserNotFound_shouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("123");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), ex.getCode());
    }

    /** 登录成功 → 返回双Token */
    @Test
    void login_whenSuccess_shouldReturnTokenData() {
        LoginRequest request = new LoginRequest();
        request.setUsername("user");
        request.setPassword("correct");

        User user = new User();
        user.setId(1L);
        user.setUsername("user");
        user.setPassword(encoder.encode("correct"));
        user.setNickname("小明");
        user.setTokenVersion(0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtUtil.generateAccessToken(1L, 0)).thenReturn("acc");
        when(jwtUtil.generateRefreshToken(1L, 0)).thenReturn("ref");

        Map<String, Object> result = userService.login(request);

        assertEquals(1L, result.get("userId"));
        assertEquals("小明", result.get("nickname"));
        assertEquals("acc", result.get("accessToken"));
        assertEquals("ref", result.get("refreshToken"));
    }

    /** refreshToken有效 → 返回新双Token */
    @Test
    void refresh_whenTokenValid_shouldReturnNewTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh");

        User user = new User();
        user.setId(1L);
        user.setNickname("小明");
        user.setTokenVersion(0);

        when(jwtUtil.getUserIdFromToken("valid-refresh")).thenReturn(1L);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtUtil.validateRefreshToken("valid-refresh", 0)).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyLong(), anyInt())).thenReturn("new-at");
        when(jwtUtil.generateRefreshToken(anyLong(), anyInt())).thenReturn("new-rt");

        Map<String, Object> result = userService.refresh(request);

        assertEquals(1L, result.get("userId"));
        assertEquals("new-at", result.get("accessToken"));
        assertEquals("new-rt", result.get("refreshToken"));
    }

    /** refreshToken过期或无效 → 抛 TOKEN_EXPIRED */
    @Test
    void refresh_whenTokenInvalid_shouldThrowException() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired");

        when(jwtUtil.getUserIdFromToken("expired")).thenReturn(1L);

        User user = new User();
        user.setId(1L);
        user.setTokenVersion(0);
        when(userMapper.selectById(1L)).thenReturn(user);

        when(jwtUtil.validateRefreshToken("expired", 0)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.refresh(request));
        assertEquals(ErrorCode.TOKEN_EXPIRED.getCode(), ex.getCode());
    }

    /** 旧密码不匹配 → 抛 LOGIN_FAILED */
    @Test
    void changePassword_wrongOldPassword_shouldThrowException() {
        User user = new User();
        user.setId(1L);
        user.setPassword(encoder.encode("right-password"));

        when(userMapper.selectById(1L)).thenReturn(user);

        assertThrows(BusinessException.class,
                () -> userService.changePassword(1L, "wrong-password", "new-pass"));
    }

    /** 旧密码正确 → 更新密码入库 */
    @Test
    void changePassword_correct_shouldUpdate() {
        User user = new User();
        user.setId(1L);
        user.setTokenVersion(0);
        user.setPassword(encoder.encode("old-pass"));

        when(userMapper.selectById(1L)).thenReturn(user);

        userService.changePassword(1L, "old-pass", "new-pass");

        verify(userMapper).updateById(user);
    }

    /** 个人中心 → 返回用户信息 + 累计步数和里程 */
    @Test
    void getProfile_success_shouldReturnProfileData() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setNickname("小明");

        when(userMapper.selectById(1L)).thenReturn(user);
        when(stepRecordMapper.sumStepsByUserId(1L)).thenReturn(10000);

        Map<String, Object> result = userService.getProfile(1L);

        assertEquals(1L, result.get("userId"));
        assertEquals("小明", result.get("nickname"));
        assertEquals(10000, result.get("totalSteps"));
        assertEquals(7.0, result.get("totalDistance"));
    }
}
