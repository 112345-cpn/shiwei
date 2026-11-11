package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.support.collections.DefaultRedisList;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock  {
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString( true);
    private static final DefaultRedisScript UNLOCK_SCRIPT ;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Boolean.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String id = ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, id , timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(b);
    }

    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name), ID_PREFIX + Thread.currentThread().getId());
        }
    /*//获取线程标识
    String id = ID_PREFIX + Thread.currentThread().getId();
    //获取锁标识
    String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    //判断是否一致
        if (id.equals(lockId)) {
        stringRedisTemplate.delete(
                this.KEY_PREFIX + name);*/
    }

