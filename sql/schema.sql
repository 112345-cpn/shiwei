-- ============================================================
-- 拾味 SHIWEI  v2  ·  本地生活双边平台
-- MySQL 8.0 / utf8mb4
--
-- 设计原则
--   1. 金额一律 int 存「分」，不用浮点
--   2. 时间一律 datetime（不用 timestamp，避开 2038）
--   3. 全表逻辑删除 deleted，不做物理删除
--   4. 评价必须挂订单 → 没消费过写不了评价，这是真实性的根
--   5. 评分冗余两份：tb_shop 上放聚合值给列表排序，
--      tb_shop_rating 上放分项明细给详情页，都由 MQ 异步刷
-- ============================================================

DROP DATABASE IF EXISTS shiwei;
CREATE DATABASE shiwei DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shiwei;

-- ------------------------------------------------------------
-- 一、账号与角色
-- ------------------------------------------------------------

CREATE TABLE `tb_user` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `phone`       varchar(11)  NOT NULL                COMMENT '手机号，登录名',
  `email`       varchar(128) DEFAULT NULL            COMMENT '邮箱，选填',
  `password`    varchar(128) NOT NULL DEFAULT ''     COMMENT 'BCrypt hash',
  `nick_name`   varchar(32)  NOT NULL DEFAULT ''     COMMENT '昵称',
  `icon`        varchar(255) NOT NULL DEFAULT ''     COMMENT '头像',
  `role`        tinyint      NOT NULL DEFAULT 0      COMMENT '0消费者 1商家 2平台运营',
  `status`      tinyint      NOT NULL DEFAULT 0      COMMENT '0正常 1封禁',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_email` (`email`)
) ENGINE=InnoDB COMMENT='用户';

CREATE TABLE `tb_user_info` (
  `user_id`     bigint unsigned NOT NULL,
  `city`        varchar(64)  NOT NULL DEFAULT '',
  `introduce`   varchar(128) NOT NULL DEFAULT '',
  `gender`      tinyint      NOT NULL DEFAULT 0      COMMENT '0未知 1男 2女',
  `birthday`    date         DEFAULT NULL,
  `fans`        int          NOT NULL DEFAULT 0,
  `followee`    int          NOT NULL DEFAULT 0,
  `review_count` int         NOT NULL DEFAULT 0      COMMENT '贡献的评价数，用于等级',
  `credit`      int          NOT NULL DEFAULT 100    COMMENT '信用分，被判定刷评会扣',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB COMMENT='用户详情';

CREATE TABLE `tb_merchant` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `owner_user_id` bigint unsigned NOT NULL           COMMENT '负责人 user_id',
  `name`          varchar(64)  NOT NULL              COMMENT '商家名称',
  `license_no`    varchar(64)  NOT NULL              COMMENT '营业执照号',
  `license_img`   varchar(255) NOT NULL DEFAULT ''   COMMENT '执照照片',
  `contact_name`  varchar(32)  NOT NULL,
  `contact_phone` varchar(11)  NOT NULL,
  `audit_status`  tinyint      NOT NULL DEFAULT 0    COMMENT '0待审 1通过 2驳回',
  `audit_remark`  varchar(255) NOT NULL DEFAULT ''   COMMENT '审核意见',
  `audit_time`    datetime     DEFAULT NULL,
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_license` (`license_no`),
  KEY `idx_owner` (`owner_user_id`),
  KEY `idx_audit` (`audit_status`, `create_time`)
) ENGINE=InnoDB COMMENT='商家主体，一个商家可有多家门店';

CREATE TABLE `tb_merchant_staff` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint unsigned NOT NULL,
  `user_id`     bigint unsigned NOT NULL,
  `staff_role`  tinyint      NOT NULL DEFAULT 1      COMMENT '0法人 1运营 2核销员',
  `shop_id`     bigint unsigned NOT NULL DEFAULT 0   COMMENT '限定门店，0表示全部门店',
  `status`      tinyint      NOT NULL DEFAULT 0      COMMENT '0在职 1离职',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_user` (`merchant_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='商家操作员，核销权限靠这张表判定';

-- ------------------------------------------------------------
-- 二、门店
-- ------------------------------------------------------------

CREATE TABLE `tb_shop_type` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `parent_id`   bigint unsigned NOT NULL DEFAULT 0  COMMENT '0为一级分类',
  `name`        varchar(32)  NOT NULL,
  `icon`        varchar(255) NOT NULL DEFAULT '',
  `sort`        int          NOT NULL DEFAULT 0,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`, `sort`)
) ENGINE=InnoDB COMMENT='商户分类，支持二级';

CREATE TABLE `tb_shop` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id`   bigint unsigned NOT NULL           COMMENT '归属商家',
  `name`          varchar(128) NOT NULL,
  `type_id`       bigint unsigned NOT NULL,
  `images`        varchar(1024) NOT NULL DEFAULT ''  COMMENT '多图逗号分隔',
  `area`          varchar(128) NOT NULL DEFAULT ''   COMMENT '商圈',
  `address`       varchar(255) NOT NULL,
  `x`             double       NOT NULL              COMMENT '经度',
  `y`             double       NOT NULL              COMMENT '纬度',
  `avg_price`     int          NOT NULL DEFAULT 0    COMMENT '人均，单位分',
  `sold`          int          NOT NULL DEFAULT 0    COMMENT '累计销量',
  `rating`        decimal(2,1) NOT NULL DEFAULT 0.0  COMMENT '总评分，冗余自 tb_shop_rating 供排序',
  `rating_count`  int          NOT NULL DEFAULT 0    COMMENT '评价数，同上',
  `status`        tinyint      NOT NULL DEFAULT 0    COMMENT '0营业 1歇业 2下线',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_merchant` (`merchant_id`),
  KEY `idx_type_status` (`type_id`, `status`),
  KEY `idx_geo` (`x`, `y`),
  KEY `idx_rating` (`rating` DESC, `rating_count` DESC)
) ENGINE=InnoDB COMMENT='门店';

CREATE TABLE `tb_shop_hours` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `shop_id`     bigint unsigned NOT NULL,
  `day_of_week` tinyint      NOT NULL                COMMENT '1-7，1为周一',
  `open_time`   time         NOT NULL,
  `close_time`  time         NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop` (`shop_id`)
) ENGINE=InnoDB COMMENT='营业时段，一天可多段（午市/晚市）';

-- ------------------------------------------------------------
-- 三、评价体系（本次重构的核心）
-- ------------------------------------------------------------

CREATE TABLE `tb_review` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id`      bigint unsigned NOT NULL           COMMENT '评价来源订单，一单一评',
  `shop_id`       bigint unsigned NOT NULL,
  `user_id`       bigint unsigned NOT NULL,
  `score_total`   tinyint      NOT NULL              COMMENT '总评 1-5',
  `score_taste`   tinyint      NOT NULL DEFAULT 0    COMMENT '口味 1-5',
  `score_env`     tinyint      NOT NULL DEFAULT 0    COMMENT '环境 1-5',
  `score_service` tinyint      NOT NULL DEFAULT 0    COMMENT '服务 1-5',
  `content`       varchar(2000) NOT NULL DEFAULT '',
  `images`        varchar(2048) NOT NULL DEFAULT ''  COMMENT '最多 9 张，逗号分隔',
  `tags`          varchar(255) NOT NULL DEFAULT ''   COMMENT '标签 id 逗号分隔',
  `useful_count`  int          NOT NULL DEFAULT 0,
  `anonymous`     tinyint      NOT NULL DEFAULT 0    COMMENT '1匿名展示',
  `status`        tinyint      NOT NULL DEFAULT 0    COMMENT '0待审 1通过 2拒绝 3隐藏',
  `audit_reason`  varchar(255) NOT NULL DEFAULT '',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_shop_status` (`shop_id`, `status`, `create_time` DESC),
  KEY `idx_user` (`user_id`, `create_time` DESC),
  KEY `idx_audit` (`status`, `create_time`)
) ENGINE=InnoDB COMMENT='评价。uk_order 从数据库层面保证一单一评';

CREATE TABLE `tb_review_reply` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `review_id`   bigint unsigned NOT NULL,
  `merchant_id` bigint unsigned NOT NULL,
  `staff_id`    bigint unsigned NOT NULL             COMMENT '回复人 user_id',
  `content`     varchar(1000) NOT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review` (`review_id`),
  KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB COMMENT='商家回复，一条评价只能回一次';

CREATE TABLE `tb_review_useful` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `review_id`   bigint unsigned NOT NULL,
  `user_id`     bigint unsigned NOT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user` (`review_id`, `user_id`)
) ENGINE=InnoDB COMMENT='「有用」投票，唯一索引防重复点';

CREATE TABLE `tb_review_tag` (
  `id`        bigint unsigned NOT NULL AUTO_INCREMENT,
  `type_id`   bigint unsigned NOT NULL DEFAULT 0    COMMENT '限定分类，0通用',
  `name`      varchar(16)  NOT NULL,
  `polarity`  tinyint      NOT NULL DEFAULT 0       COMMENT '1正面 -1负面 0中性',
  `sort`      int          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type_id`, `sort`)
) ENGINE=InnoDB COMMENT='评价标签字典，喂料给「评价关键词」聚合';

CREATE TABLE `tb_shop_rating` (
  `shop_id`     bigint unsigned NOT NULL,
  `review_count` int         NOT NULL DEFAULT 0,
  `avg_total`   decimal(3,2) NOT NULL DEFAULT 0.00,
  `avg_taste`   decimal(3,2) NOT NULL DEFAULT 0.00,
  `avg_env`     decimal(3,2) NOT NULL DEFAULT 0.00,
  `avg_service` decimal(3,2) NOT NULL DEFAULT 0.00,
  `star1`       int          NOT NULL DEFAULT 0,
  `star2`       int          NOT NULL DEFAULT 0,
  `star3`       int          NOT NULL DEFAULT 0,
  `star4`       int          NOT NULL DEFAULT 0,
  `star5`       int          NOT NULL DEFAULT 0,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`shop_id`)
) ENGINE=InnoDB COMMENT='评分聚合，由 MQ 消费者增量刷新，定时任务兜底重算';

-- ------------------------------------------------------------
-- 四、内容社区
-- ------------------------------------------------------------

CREATE TABLE `tb_blog` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT,
  `shop_id`    bigint unsigned NOT NULL DEFAULT 0   COMMENT '关联门店，0为纯攻略',
  `user_id`    bigint unsigned NOT NULL,
  `title`      varchar(255) NOT NULL DEFAULT '',
  `images`     varchar(2048) NOT NULL DEFAULT '',
  `content`    varchar(2048) NOT NULL DEFAULT '',
  `liked`      int          NOT NULL DEFAULT 0,
  `comments`   int          NOT NULL DEFAULT 0,
  `status`     tinyint      NOT NULL DEFAULT 0      COMMENT '0待审 1通过 2拒绝',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`, `create_time` DESC),
  KEY `idx_shop` (`shop_id`),
  KEY `idx_status_time` (`status`, `create_time` DESC)
) ENGINE=InnoDB COMMENT='探店笔记';

CREATE TABLE `tb_blog_comments` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `blog_id`     bigint unsigned NOT NULL,
  `user_id`     bigint unsigned NOT NULL,
  `parent_id`   bigint unsigned NOT NULL DEFAULT 0  COMMENT '一级评论 id',
  `answer_id`   bigint unsigned NOT NULL DEFAULT 0  COMMENT '回复的评论 id',
  `content`     varchar(500) NOT NULL,
  `liked`       int          NOT NULL DEFAULT 0,
  `status`      tinyint      NOT NULL DEFAULT 0,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_blog` (`blog_id`, `create_time`)
) ENGINE=InnoDB COMMENT='笔记评论';

CREATE TABLE `tb_blog_like` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `blog_id`     bigint unsigned NOT NULL,
  `user_id`     bigint unsigned NOT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_blog_user` (`blog_id`, `user_id`)
) ENGINE=InnoDB COMMENT='点赞记录，唯一索引防重复';

CREATE TABLE `tb_follow` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`        bigint unsigned NOT NULL,
  `follow_user_id` bigint unsigned NOT NULL,
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pair` (`user_id`, `follow_user_id`),
  KEY `idx_followed` (`follow_user_id`)
) ENGINE=InnoDB COMMENT='关注关系';

-- ------------------------------------------------------------
-- 五、商品与交易
-- ------------------------------------------------------------

CREATE TABLE `tb_voucher` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
  `merchant_id`    bigint unsigned NOT NULL,
  `shop_id`        bigint unsigned NOT NULL,
  `title`          varchar(64)  NOT NULL,
  `sub_title`      varchar(128) NOT NULL DEFAULT '',
  `rules`          varchar(1000) NOT NULL DEFAULT '' COMMENT '使用规则',
  `type`           tinyint      NOT NULL DEFAULT 0   COMMENT '0代金券 1套餐 2单品',
  `original_price` int          NOT NULL DEFAULT 0   COMMENT '原价，分',
  `sale_price`     int          NOT NULL DEFAULT 0   COMMENT '售价，分',
  `stock`          int          NOT NULL DEFAULT 0,
  `sold`           int          NOT NULL DEFAULT 0,
  `sale_begin`     datetime     NOT NULL             COMMENT '售卖开始',
  `sale_end`       datetime     NOT NULL             COMMENT '售卖结束',
  `valid_begin`    datetime     NOT NULL             COMMENT '使用有效期开始',
  `valid_end`      datetime     NOT NULL             COMMENT '使用有效期结束',
  `status`         tinyint      NOT NULL DEFAULT 0   COMMENT '0草稿 1上架 2下架',
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_shop_status` (`shop_id`, `status`),
  KEY `idx_merchant` (`merchant_id`),
  KEY `idx_sale` (`status`, `sale_begin`, `sale_end`)
) ENGINE=InnoDB COMMENT='券/套餐，由商家上架';

CREATE TABLE `tb_seckill_voucher` (
  `voucher_id` bigint unsigned NOT NULL,
  `stock`      int          NOT NULL                COMMENT 'Redis 里的权威库存，DB 只做兜底',
  `begin_time` datetime     NOT NULL,
  `end_time`   datetime     NOT NULL,
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`voucher_id`)
) ENGINE=InnoDB COMMENT='限时抢购扩展表';

CREATE TABLE `tb_order` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_no`       varchar(32)  NOT NULL             COMMENT '业务单号',
  `user_id`        bigint unsigned NOT NULL,
  `voucher_id`     bigint unsigned NOT NULL,
  `shop_id`        bigint unsigned NOT NULL,
  `merchant_id`    bigint unsigned NOT NULL,
  `title`          varchar(64)  NOT NULL             COMMENT '下单时快照',
  `quantity`       int          NOT NULL DEFAULT 1,
  `unit_price`     int          NOT NULL             COMMENT '快照单价，分',
  `pay_amount`     int          NOT NULL             COMMENT '实付，分',
  `status`         tinyint      NOT NULL DEFAULT 0   COMMENT '0待支付 1待使用 2已核销 3退款中 4已退款 5已关闭',
  `verify_code`    varchar(16)  NOT NULL             COMMENT '核销码',
  `pay_time`       datetime     DEFAULT NULL,
  `verify_time`    datetime     DEFAULT NULL,
  `verify_staff_id` bigint unsigned DEFAULT NULL,
  `expire_time`    datetime     NOT NULL             COMMENT '超时未支付自动关单',
  `refund_time`    datetime     DEFAULT NULL,
  `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_verify_code` (`verify_code`),
  KEY `idx_user_status` (`user_id`, `status`, `create_time` DESC),
  KEY `idx_shop` (`shop_id`, `status`),
  KEY `idx_merchant` (`merchant_id`, `status`),
  KEY `idx_close` (`status`, `expire_time`)
) ENGINE=InnoDB COMMENT='订单，状态机：0→1→2，1→3→4，0→5';

CREATE TABLE `tb_payment` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `pay_no`      varchar(32)  NOT NULL,
  `order_id`    bigint unsigned NOT NULL,
  `channel`     tinyint      NOT NULL DEFAULT 0      COMMENT '0余额 1模拟支付 2支付宝',
  `amount`      int          NOT NULL,
  `status`      tinyint      NOT NULL DEFAULT 0      COMMENT '0待付 1成功 2失败 3已退',
  `external_no` varchar(64)  NOT NULL DEFAULT ''     COMMENT '渠道流水号',
  `notify_time` datetime     DEFAULT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_external` (`external_no`)
) ENGINE=InnoDB COMMENT='支付流水，一单一条';

CREATE TABLE `tb_refund` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `refund_no`   varchar(32)  NOT NULL,
  `order_id`    bigint unsigned NOT NULL,
  `payment_id`  bigint unsigned NOT NULL,
  `amount`      int          NOT NULL,
  `reason`      varchar(255) NOT NULL DEFAULT '',
  `status`      tinyint      NOT NULL DEFAULT 0      COMMENT '0待审 1已同意 2已退款 3已驳回',
  `audit_staff_id` bigint unsigned DEFAULT NULL,
  `audit_time`  datetime     DEFAULT NULL,
  `finish_time` datetime     DEFAULT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='退款单';

CREATE TABLE `tb_verify_record` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `order_id`    bigint unsigned NOT NULL,
  `verify_code` varchar(16)  NOT NULL,
  `shop_id`     bigint unsigned NOT NULL,
  `staff_id`    bigint unsigned NOT NULL             COMMENT '核销人 user_id',
  `amount`      int          NOT NULL,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_shop_time` (`shop_id`, `create_time`),
  KEY `idx_staff` (`staff_id`)
) ENGINE=InnoDB COMMENT='核销记录。uk_order 保证同一单只能核销一次，靠数据库兜住幂等';

CREATE TABLE `tb_reconciliation` (
  `id`            bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_no`      varchar(32)  NOT NULL,
  `merchant_id`   bigint unsigned NOT NULL,
  `stat_date`     date         NOT NULL,
  `order_count`   int          NOT NULL DEFAULT 0,
  `total_amount`  int          NOT NULL DEFAULT 0    COMMENT '核销总额',
  `refund_amount` int          NOT NULL DEFAULT 0,
  `settle_amount` int          NOT NULL DEFAULT 0    COMMENT '应结给商家',
  `status`        tinyint      NOT NULL DEFAULT 0    COMMENT '0待核 1已平 2有差异',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch` (`batch_no`),
  KEY `idx_merchant_date` (`merchant_id`, `stat_date`)
) ENGINE=InnoDB COMMENT='日对账批次';

CREATE TABLE `tb_reconciliation_detail` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT,
  `batch_id`     bigint unsigned NOT NULL,
  `order_id`     bigint unsigned NOT NULL,
  `our_amount`   int          NOT NULL               COMMENT '我方记录',
  `channel_amount` int        NOT NULL DEFAULT 0     COMMENT '渠道记录',
  `diff_type`    tinyint      NOT NULL DEFAULT 0     COMMENT '0平 1我方多 2渠道多 3单边账',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_batch` (`batch_id`, `diff_type`)
) ENGINE=InnoDB COMMENT='对账明细，diff_type<>0 的进人工处理队列';

-- ------------------------------------------------------------
-- 六、发现与统计
-- ------------------------------------------------------------

CREATE TABLE `tb_search_history` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`     bigint unsigned NOT NULL DEFAULT 0   COMMENT '0为匿名',
  `keyword`     varchar(64)  NOT NULL,
  `result_count` int         NOT NULL DEFAULT 0,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time` DESC),
  KEY `idx_keyword` (`keyword`)
) ENGINE=InnoDB COMMENT='搜索历史，同时喂热搜榜';

CREATE TABLE `tb_shop_stats_daily` (
  `id`             bigint unsigned NOT NULL AUTO_INCREMENT,
  `shop_id`        bigint unsigned NOT NULL,
  `stat_date`      date         NOT NULL,
  `view_count`     int          NOT NULL DEFAULT 0,
  `order_count`    int          NOT NULL DEFAULT 0,
  `verify_count`   int          NOT NULL DEFAULT 0,
  `gmv`            int          NOT NULL DEFAULT 0,
  `review_count`   int          NOT NULL DEFAULT 0,
  `avg_rating`     decimal(2,1) NOT NULL DEFAULT 0.0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_date` (`shop_id`, `stat_date`)
) ENGINE=InnoDB COMMENT='门店日统计，商家看板的数据源，离线聚合';

-- ------------------------------------------------------------
-- 七、运营支撑
-- ------------------------------------------------------------

CREATE TABLE `tb_notification` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`     bigint unsigned NOT NULL,
  `type`        tinyint      NOT NULL DEFAULT 0      COMMENT '0系统 1订单 2评价 3审核',
  `title`       varchar(64)  NOT NULL,
  `content`     varchar(500) NOT NULL DEFAULT '',
  `biz_id`      bigint unsigned NOT NULL DEFAULT 0   COMMENT '关联业务 id',
  `read_flag`   tinyint      NOT NULL DEFAULT 0,
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `read_flag`, `create_time` DESC)
) ENGINE=InnoDB COMMENT='站内消息';

CREATE TABLE `tb_audit_log` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT,
  `biz_type`    tinyint      NOT NULL                COMMENT '1商家 2评价 3笔记 4退款',
  `biz_id`      bigint unsigned NOT NULL,
  `auditor_id`  bigint unsigned NOT NULL,
  `action`      tinyint      NOT NULL                COMMENT '1通过 2驳回 3隐藏',
  `remark`      varchar(255) NOT NULL DEFAULT '',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB COMMENT='审核流水，出事能追溯是谁放的行';

CREATE TABLE `tb_sign` (
  `id`        bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id`   bigint unsigned NOT NULL,
  `sign_date` date         NOT NULL,
  `is_backup` tinyint      NOT NULL DEFAULT 0,
  `create_time` datetime   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `sign_date`)
) ENGINE=InnoDB COMMENT='签到，唯一索引取代原来「查了再插」的并发漏洞';
