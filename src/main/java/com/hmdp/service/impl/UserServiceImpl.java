package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    RedisTemplate stringRedisTemplate;

    @Resource
    private final UserMapper userMapper;

    /**
     * 是否把验证码明文返回给前端。
     *
     * 这个项目没有接短信通道，验证码只写进日志 —— 本地自测时得去 logs/hmdp.log 里翻，
     * 而且 service 包的 debug 级别在 yaml 里被压到 info，其实连日志都看不到，
     * 结果就是「点了获取验证码，然后永远登录不进去」。
     *
     * 所以给个开关：dev 打开（验证码直接填进登录框），prod 必须关闭。
     * 生产打开等于把账号体系敞开了。
     */
    @org.springframework.beans.factory.annotation.Value("${hmdp.auth.expose-code:false}")
    private boolean exposeCode;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Result sedcode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        if (exposeCode) {
            log.info("【开发模式】手机号 {} 的验证码是 {}（生产环境请关闭 hmdp.auth.expose-code）", phone, code);
            Map<String, Object> data = new HashMap<>(2);
            data.put("code", code);
            return Result.ok(data);
        }

        log.debug("发送验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone).toString();
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.equals(code)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
            save(user);
        }
        // 1. 生成token
        String token = UUID.randomUUID().toString(true);

// 2. 将User对象转为UserDTO
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

// 3. 将UserDTO转为Map保存到Object（关键：把Long型的ID转成String）
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

// 4. 存储到Redis（注意：前缀一定要加冒号 ":" ！！）
        String tokenKey = "login:token:" + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

// 5. 设置有效期（注意：用 LOGIN_USER_TTL，别用那个2分钟的 CODE_TTL 了）
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

// 6. 返回token
        return Result.ok(token);

    }

    @Override
    public Result sign() {
        //1. 获取当前登录用户
        Long id = UserHolder.getUser().getId();
        //2. 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
         String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
String key="sign:"+id+keySuffix;
        //4.获取今天是第几天
        int dayOfMonth = now.getDayOfMonth();
        //5.写入redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    @Override
    public Result signcount() {
        Long id = UserHolder.getUser().getId();
        //2. 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        //3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key="sign:"+id+keySuffix;
        //4.获取今天是第几天
        int dayOfMonth = now.getDayOfMonth();
        //6.获取本月截止今天的签到次数，返回的是十进制
        List result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if(result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num = (Long) result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count=0;
        //7.遍历循环
        while(true){
            //8.与1做与运算，返回最后一个bit为
            if((num&1)==0){
                //是则未签到
                break;
            }
            //判断是否为0
            else{
                //否则已签到，计数器加一
                count++;

            }
            num>>>=1;
        }
        return Result.ok(count);
    }
}
