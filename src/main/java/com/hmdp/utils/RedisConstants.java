package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀一人一单集合 Key 前缀（Lua 脚本里用 sadd/sismember 操作） */
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // ============================================
    // 缓存三连专用常量（Step 6 新增）
    // ============================================

    /** 空值标识（防止缓存穿透） */
    public static final String CACHE_NULL_VALUE = "__NULL__";

    /** 缓存空值 TTL（2 分钟） */
    public static final Long CACHE_NULL_EXPIRE = 2L;

    /** 默认缓存 TTL（30 分钟） */
    public static final Long CACHE_DEFAULT_TTL = 30L;

    /** 默认缓存 TTL 随机偏移（防止雪崩） */
    public static final Long CACHE_RANDOM_TTL_RANGE = 5L;

    /** 逻辑过期 TTL（30 分钟） */
    public static final Long CACHE_LOGIC_EXPIRE_TTL = 30L;

    /** Redisson 分布式锁 Key 前缀 */
    public static final String LOCK_KEY_PREFIX = "lock:";

    /** 布隆过滤器 Key 前缀（Redis 版 BloomFilter） */
    public static final String BLOOM_FILTER_KEY = "bloom:filter:";
}
