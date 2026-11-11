package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    // 1. 这里直接用 StringRedisTemplate，不要在构造函数里强转
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        // 【测谎仪 1：前端交票了吗？】
        System.err.println("【1. 前端传来的Token】: " + token);

        if (StrUtil.isBlank(token)) {
            return true;
        }

        String key = "login:token:" + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 【测谎仪 2：Redis 里面有这个人吗？】
        System.err.println("【2. Redis查出的Map】: " + userMap);

        if (userMap.isEmpty()) {
            return true;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 【测谎仪 3：UserDTO 转换成功了吗？是不是因为没 Lombok 变成空壳了？】
        System.err.println("【3. 转换后的UserDTO】: " + userDTO.getId() + " | " + userDTO.getNickName());

        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，防止内存泄漏
        UserHolder.removeUser();
    }
}
