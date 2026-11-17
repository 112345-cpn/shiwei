package com.shiwei.common.constant;

public final class RedisKeys {

    public static final String TOKEN_PREFIX = "shiwei:token:";
    public static final String USER_TOKENS_PREFIX = "shiwei:user:tokens:";
    public static final String LOGIN_CODE_PREFIX = "shiwei:code:";
    public static final String CODE_INTERVAL_PREFIX = "shiwei:code:interval:";

    public static final String SHOP_DETAIL_PREFIX = "shiwei:shop:";
    public static final String SHOP_TYPE_LIST = "shiwei:shop:types";
    public static final String SHOP_RATING_PREFIX = "shiwei:rating:";

    public static final String SECKILL_STOCK_PREFIX = "shiwei:seckill:stock:";
    public static final String SECKILL_ORDER_USER = "shiwei:seckill:order:user:";

    public static final String VOUCHER_STOCK_PREFIX = "shiwei:stock:voucher:";
    public static final String ORDER_ID_KEY = "shiwei:order:id";
    public static final String PAY_ID_KEY = "shiwei:pay:id";
    public static final String REFUND_ID_KEY = "shiwei:refund:id";

    public static final String GEO_SHOP_KEY = "shiwei:geo:shops";
    public static final String HOT_SEARCH_KEY = "shiwei:hot:search";

    public static final String REVIEW_LIKE_LOCK = "shiwei:lock:review:useful:";
    public static final String VERIFY_LOCK = "shiwei:lock:verify:";

    private RedisKeys() {
    }

    public static String userTokens(Long userId) {
        return USER_TOKENS_PREFIX + userId;
    }

    /** 秒杀库存，String 类型，值是剩余张数 */
    public static String seckillStock(Long voucherId) {
        return SECKILL_STOCK_PREFIX + voucherId;
    }

    /** 已经抢到的用户集合，一人一单靠它挡 */
    public static String seckillUsers(Long voucherId) {
        return SECKILL_ORDER_USER + voucherId;
    }

    /** 单个用户的抢购结果，给前端轮询用 */
    public static String seckillResult(Long voucherId, Long userId) {
        return "shiwei:seckill:result:" + voucherId + ":" + userId;
    }
}
