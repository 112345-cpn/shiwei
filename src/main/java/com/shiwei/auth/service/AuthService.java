package com.shiwei.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shiwei.auth.dto.CodeLoginForm;
import com.shiwei.auth.dto.PasswordLoginForm;
import com.shiwei.auth.dto.RegisterForm;
import com.shiwei.auth.entity.User;
import com.shiwei.auth.entity.UserInfo;
import com.shiwei.auth.mapper.UserInfoMapper;
import com.shiwei.auth.mapper.UserMapper;
import com.shiwei.auth.vo.LoginVO;
import com.shiwei.auth.vo.UserVO;
import com.shiwei.common.security.TokenStore;
import com.shiwei.common.constant.RedisKeys;
import com.shiwei.common.enums.RoleType;
import com.shiwei.common.exception.BizException;
import com.shiwei.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserInfoMapper userInfoMapper;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    @Value("${shiwei.auth.code-ttl-minutes:5}")
    private int codeTtlMinutes;

    @Value("${shiwei.auth.code-interval-seconds:60}")
    private int codeIntervalSeconds;

    @Value("${shiwei.auth.token-ttl-days:30}")
    private int tokenTtlDays;

    @Value("${shiwei.auth.expose-code:false}")
    private boolean exposeCode;

    /**
     * 发送登录验证码。间隔限制放在 Redis 里做，多实例同样生效。
     */
    public String sendCode(String phone) {
        String intervalKey = RedisKeys.CODE_INTERVAL_PREFIX + phone;
        Boolean first = redis.opsForValue().setIfAbsent(intervalKey, "1", Duration.ofSeconds(codeIntervalSeconds));
        if (Boolean.FALSE.equals(first)) {
            throw BizException.of(ErrorCode.AUTH_CODE_TOO_FREQUENT);
        }

        String code = RandomUtil.randomNumbers(6);
        redis.opsForValue().set(RedisKeys.LOGIN_CODE_PREFIX + phone, code, Duration.ofMinutes(codeTtlMinutes));
        log.info("登录验证码 phone={} code={}", phone, code);

        return exposeCode ? code : null;
    }

    /**
     * 验证码登录，手机号未注册时直接建号。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO loginByCode(CodeLoginForm form) {
        checkCode(form.getPhone(), form.getCode());

        User user = findByPhone(form.getPhone());
        if (user == null) {
            user = createUser(form.getPhone(), null, null);
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw BizException.of(ErrorCode.AUTH_ACCOUNT_BANNED);
        }
        return issueToken(user);
    }

    public LoginVO loginByPassword(PasswordLoginForm form) {
        User user = findByPhone(form.getPhone());
        if (user == null || user.getPassword() == null || user.getPassword().isEmpty()) {
            throw BizException.of(ErrorCode.AUTH_BAD_CREDENTIAL);
        }
        if (!passwordEncoder.matches(form.getPassword(), user.getPassword())) {
            throw BizException.of(ErrorCode.AUTH_BAD_CREDENTIAL);
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw BizException.of(ErrorCode.AUTH_ACCOUNT_BANNED);
        }
        return issueToken(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterForm form) {
        checkCode(form.getPhone(), form.getCode());
        if (findByPhone(form.getPhone()) != null) {
            throw BizException.of(ErrorCode.USER_PHONE_EXISTS);
        }
        User user = createUser(form.getPhone(), form.getPassword(), form.getNickName());
        return issueToken(user);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            tokenStore.remove(token, null);
        }
    }

    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    private void checkCode(String phone, String code) {
        String key = RedisKeys.LOGIN_CODE_PREFIX + phone;
        String cached = redis.opsForValue().get(key);
        if (cached == null || !cached.equals(code)) {
            throw BizException.of(ErrorCode.AUTH_CODE_INVALID);
        }
        redis.delete(key);
    }

    private User findByPhone(String phone) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getPhone, phone));
    }

    private User createUser(String phone, String rawPassword, String nickName) {
        User user = new User();
        user.setPhone(phone);
        user.setPassword(rawPassword == null ? "" : passwordEncoder.encode(rawPassword));
        user.setNickName(nickName == null || nickName.isBlank() ? "拾味用户" + phone.substring(7) : nickName);
        user.setIcon("/imgs/avatar/default.png");
        user.setRole(RoleType.CONSUMER.getCode());
        user.setStatus(0);
        userMapper.insert(user);

        UserInfo info = new UserInfo();
        info.setUserId(user.getId());
        info.setCity("桂林");
        info.setIntroduce("");
        info.setGender(0);
        info.setFans(0);
        info.setFollowee(0);
        info.setReviewCount(0);
        info.setCredit(100);
        userInfoMapper.insert(info);

        return user;
    }

    private LoginVO issueToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.save(token, user, tokenTtlDays);
        return new LoginVO(token, toVO(user), null);
    }

    private UserVO toVO(User user) {
        return new UserVO(user.getId(), user.getPhone(), user.getNickName(), user.getIcon(), user.getRole());
    }
}
