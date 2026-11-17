-- ============================================================
-- 拾味 SHIWEI v2  ·  种子数据
-- 所有账号密码：123456
-- 时间均以 NOW() 为基准偏移，保证任何时候导入都处于有效期内
-- ============================================================

USE shiwei;

SET @pwd = '$2b$10$l6Qf4a0jBRiCpBplmCIJ6ebjAuySSnPOpHKZC56.Y.fEFkT7DR7dm';

-- ------------------------------------------------------------
-- 用户：1 运营 / 2-3 商家 / 4-12 消费者
-- ------------------------------------------------------------
INSERT INTO `tb_user` (`id`,`phone`,`password`,`nick_name`,`icon`,`role`) VALUES
(1,'13800000001',@pwd,'平台运营','/imgs/avatar/ops.png',2),
(2,'13800000002',@pwd,'周记米粉','/imgs/avatar/m1.png',1),
(3,'13800000003',@pwd,'山间咖啡主理人','/imgs/avatar/m2.png',1),
(4,'13800000004',@pwd,'吃货小林','/imgs/avatar/u4.png',0),
(5,'13800000005',@pwd,'阿May','/imgs/avatar/u5.png',0),
(6,'13800000006',@pwd,'漓江边散步','/imgs/avatar/u6.png',0),
(7,'13800000007',@pwd,'不吃香菜','/imgs/avatar/u7.png',0),
(8,'13800000008',@pwd,'周末探店','/imgs/avatar/u8.png',0),
(9,'13800000009',@pwd,'Tina','/imgs/avatar/u9.png',0),
(10,'13800000010',@pwd,'老桂林','/imgs/avatar/u10.png',0),
(11,'13800000011',@pwd,'咖啡因依赖','/imgs/avatar/u11.png',0),
(12,'13800000012',@pwd,'早八人','/imgs/avatar/u12.png',0);

INSERT INTO `tb_user_info` (`user_id`,`city`,`introduce`,`gender`,`fans`,`followee`,`review_count`,`credit`) VALUES
(1,'桂林','拾味平台运营',0,0,0,0,100),
(2,'桂林','做了二十年米粉',1,120,8,0,100),
(3,'桂林','一杯咖啡一座城',2,86,12,0,100),
(4,'桂林','吃遍七星区',1,42,55,3,100),
(5,'桂林','甜品雷达',2,18,33,3,100),
(6,'桂林','喜欢走路和吃饭',0,9,21,3,100),
(7,'桂林','香菜退散',1,5,12,3,100),
(8,'桂林','周末只有两件事：睡和吃',0,27,40,0,100),
(9,'桂林','摄影+咖啡',2,63,28,0,100),
(10,'桂林','土著，只推荐真好吃的',1,210,16,0,100),
(11,'桂林','一天两杯',0,14,9,0,100),
(12,'桂林','早八续命',0,3,7,0,100);

-- ------------------------------------------------------------
-- 商家主体
-- ------------------------------------------------------------
INSERT INTO `tb_merchant` (`id`,`owner_user_id`,`name`,`license_no`,`license_img`,`contact_name`,`contact_phone`,`audit_status`,`audit_remark`,`audit_time`) VALUES
(1,2,'桂林周记餐饮管理有限公司','91450300MA5KX0001A','/imgs/license/m1.jpg','周建国','13800000002',1,'资质齐全',DATE_SUB(NOW(), INTERVAL 90 DAY)),
(2,3,'桂林山间咖啡有限公司','91450300MA5KX0002B','/imgs/license/m2.jpg','李山','13800000003',1,'资质齐全',DATE_SUB(NOW(), INTERVAL 60 DAY)),
(3,2,'桂林漓江渔火餐饮有限公司','91450300MA5KX0003C','/imgs/license/m3.jpg','周建国','13800000002',1,'资质齐全',DATE_SUB(NOW(), INTERVAL 45 DAY)),
(4,9,'桂林椿记餐饮有限公司','91450300MA5KX0004D','/imgs/license/m4.jpg','黄椿','13800000009',0,'',NULL);

INSERT INTO `tb_merchant_staff` (`merchant_id`,`user_id`,`staff_role`,`shop_id`,`status`) VALUES
(1,2,0,0,0),
(2,3,0,0,0),
(3,2,0,0,0),
(4,9,0,0,0),
(1,7,2,1,0),
(2,11,2,0,0);

-- ------------------------------------------------------------
-- 分类（一级 + 二级）
-- ------------------------------------------------------------
INSERT INTO `tb_shop_type` (`id`,`parent_id`,`name`,`icon`,`sort`) VALUES
(1,0,'美食','/imgs/type/food.png',1),
(2,0,'休闲娱乐','/imgs/type/fun.png',2),
(3,0,'丽人','/imgs/type/beauty.png',3),
(11,1,'米粉','/imgs/type/noodle.png',1),
(12,1,'火锅','/imgs/type/hotpot.png',2),
(13,1,'烧烤','/imgs/type/bbq.png',3),
(14,1,'粤菜','/imgs/type/yue.png',4),
(15,1,'咖啡甜品','/imgs/type/coffee.png',5),
(21,2,'KTV','/imgs/type/ktv.png',1),
(22,2,'电影','/imgs/type/movie.png',2),
(31,3,'美发','/imgs/type/hair.png',1);

-- ------------------------------------------------------------
-- 门店
-- ------------------------------------------------------------
INSERT INTO `tb_shop` (`id`,`merchant_id`,`name`,`type_id`,`images`,`area`,`address`,`x`,`y`,`avg_price`,`sold`,`rating`,`rating_count`,`status`) VALUES
(1,1,'老四样米粉（七星路店）',11,'/imgs/shop/1-1.jpg,/imgs/shop/1-2.jpg','七星区','七星区七星路 18 号',110.3152,25.2735,1200,3820,4.7,3,0),
(2,1,'老四样米粉（中山路店）',11,'/imgs/shop/2-1.jpg','象山区','象山区中山中路 66 号',110.2901,25.2621,1100,2510,0.0,0,0),
(3,2,'山间咖啡（东西巷店）',15,'/imgs/shop/3-1.jpg,/imgs/shop/3-2.jpg','秀峰区','秀峰区东西巷 12 号',110.2965,25.2805,3800,1560,4.3,3,0),
(4,2,'山间咖啡（万象城店）',15,'/imgs/shop/4-1.jpg','临桂区','临桂区世纪大道万象城 3F',110.2098,25.2402,4200,890,0.0,0,0),
(5,3,'漓江渔火（象山店）',14,'/imgs/shop/5-1.jpg,/imgs/shop/5-2.jpg','象山区','象山区滨江路 8 号',110.2988,25.2568,8800,1240,4.0,3,0),
(6,3,'漓江渔火（叠彩店）',14,'/imgs/shop/6-1.jpg','叠彩区','叠彩区中山北路 200 号',110.2905,25.2920,8200,640,0.0,0,1),
(7,1,'老盐街涮羊肉',12,'/imgs/shop/7-1.jpg','秀峰区','秀峰区老盐街 3 号',110.2941,25.2782,6500,2100,4.0,3,0),
(8,4,'椿记烧鹅（解放桥店）',14,'/imgs/shop/8-1.jpg','象山区','象山区解放东路 1 号',110.2977,25.2666,7600,4300,0.0,0,2);

INSERT INTO `tb_shop_hours` (`shop_id`,`day_of_week`,`open_time`,`close_time`) VALUES
(1,1,'07:00','14:00'),(1,2,'07:00','14:00'),(1,3,'07:00','14:00'),(1,4,'07:00','14:00'),(1,5,'07:00','14:00'),(1,6,'07:00','15:00'),(1,7,'07:00','15:00'),
(1,1,'17:00','21:00'),(1,2,'17:00','21:00'),(1,3,'17:00','21:00'),(1,4,'17:00','21:00'),(1,5,'17:00','21:00'),
(2,1,'07:00','20:00'),(2,2,'07:00','20:00'),(2,3,'07:00','20:00'),(2,4,'07:00','20:00'),(2,5,'07:00','20:00'),(2,6,'07:00','20:00'),(2,7,'07:00','20:00'),
(3,1,'10:00','22:00'),(3,2,'10:00','22:00'),(3,3,'10:00','22:00'),(3,4,'10:00','22:00'),(3,5,'10:00','23:00'),(3,6,'10:00','23:00'),(3,7,'10:00','23:00'),
(4,1,'10:00','22:00'),(4,2,'10:00','22:00'),(4,3,'10:00','22:00'),(4,4,'10:00','22:00'),(4,5,'10:00','22:00'),(4,6,'10:00','22:00'),(4,7,'10:00','22:00'),
(5,1,'11:00','14:00'),(5,2,'11:00','14:00'),(5,3,'11:00','14:00'),(5,4,'11:00','14:00'),(5,5,'11:00','14:00'),(5,6,'11:00','14:00'),(5,7,'11:00','14:00'),
(5,1,'17:00','21:30'),(5,2,'17:00','21:30'),(5,3,'17:00','21:30'),(5,4,'17:00','21:30'),(5,5,'17:00','21:30'),(5,6,'17:00','21:30'),(5,7,'17:00','21:30'),
(7,1,'17:00','02:00'),(7,2,'17:00','02:00'),(7,3,'17:00','02:00'),(7,4,'17:00','02:00'),(7,5,'17:00','02:00'),(7,6,'17:00','02:00'),(7,7,'17:00','02:00');

-- ------------------------------------------------------------
-- 评价标签
-- ------------------------------------------------------------
INSERT INTO `tb_review_tag` (`id`,`type_id`,`name`,`polarity`,`sort`) VALUES
(1,0,'味道正宗',1,1),
(2,0,'分量足',1,2),
(3,0,'性价比高',1,3),
(4,0,'环境舒服',1,4),
(5,0,'服务态度好',1,5),
(6,0,'出餐快',1,6),
(7,0,'排队久',-1,1),
(8,0,'偏贵',-1,2),
(9,0,'卫生一般',-1,3),
(10,0,'太吵',-1,4),
(11,11,'卤水香',1,1),
(12,15,'豆子好',1,1),
(13,15,'适合办公',1,2);

-- ------------------------------------------------------------
-- 券
-- ------------------------------------------------------------
INSERT INTO `tb_voucher` (`id`,`merchant_id`,`shop_id`,`title`,`sub_title`,`rules`,`type`,`original_price`,`sale_price`,`stock`,`sold`,`sale_begin`,`sale_end`,`valid_begin`,`valid_end`,`status`) VALUES
(1,1,1,'招牌卤菜粉套餐','二两卤菜粉+卤蛋+豆浆','仅限堂食，不可与其他优惠同享',1,1800,1280,500,1820,DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_ADD(NOW(), INTERVAL 60 DAY),DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_ADD(NOW(), INTERVAL 90 DAY),1),
(2,1,1,'10 元代金券','满 25 元可用','单桌限用 2 张',0,1000,800,800,640,DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_ADD(NOW(), INTERVAL 60 DAY),DATE_SUB(NOW(), INTERVAL 30 DAY),DATE_ADD(NOW(), INTERVAL 90 DAY),1),
(3,2,3,'双人下午茶','两杯手冲+一份提拉米苏','需提前 2 小时预约',1,12800,8800,200,73,DATE_SUB(NOW(), INTERVAL 20 DAY),DATE_ADD(NOW(), INTERVAL 40 DAY),DATE_SUB(NOW(), INTERVAL 20 DAY),DATE_ADD(NOW(), INTERVAL 80 DAY),1),
(4,2,3,'单人美式','工作日全天可用','每人每天限购 1 张',0,2800,1900,1000,412,DATE_SUB(NOW(), INTERVAL 20 DAY),DATE_ADD(NOW(), INTERVAL 40 DAY),DATE_SUB(NOW(), INTERVAL 20 DAY),DATE_ADD(NOW(), INTERVAL 80 DAY),1),
(5,3,5,'四人漓江鱼宴','啤酒鱼+田螺酿+时蔬+汤','需提前一天预约，节假日不可用',1,36800,29800,120,46,DATE_SUB(NOW(), INTERVAL 15 DAY),DATE_ADD(NOW(), INTERVAL 45 DAY),DATE_SUB(NOW(), INTERVAL 15 DAY),DATE_ADD(NOW(), INTERVAL 75 DAY),1),
(6,1,7,'涮羊肉双人餐','羊肉两份+涮菜拼盘+饮料','限晚市使用',1,19800,15800,300,208,DATE_SUB(NOW(), INTERVAL 10 DAY),DATE_ADD(NOW(), INTERVAL 50 DAY),DATE_SUB(NOW(), INTERVAL 10 DAY),DATE_ADD(NOW(), INTERVAL 70 DAY),1),
(7,2,4,'开业尝鲜券','任意饮品立减 10 元','开业首月专享',0,1000,0,100,0,DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_ADD(NOW(), INTERVAL 27 DAY),DATE_SUB(NOW(), INTERVAL 3 DAY),DATE_ADD(NOW(), INTERVAL 57 DAY),1);

-- 秒杀场次：开始时间设在 1 小时前，pull 下来就能直接测，不用等到明天
INSERT INTO `tb_seckill_voucher` (`voucher_id`,`stock`,`begin_time`,`end_time`) VALUES
(7,100,DATE_SUB(NOW(), INTERVAL 1 HOUR),DATE_ADD(NOW(), INTERVAL 7 DAY));

-- ------------------------------------------------------------
-- 已核销订单（评价必须挂在这些订单上）
-- ------------------------------------------------------------
INSERT INTO `tb_order` (`id`,`order_no`,`user_id`,`voucher_id`,`shop_id`,`merchant_id`,`title`,`quantity`,`unit_price`,`pay_amount`,`status`,`verify_code`,`pay_time`,`verify_time`,`verify_staff_id`,`expire_time`,`create_time`) VALUES
(1,'SW20261001000001',4,1,1,1,'招牌卤菜粉套餐',1,1280,1280,2,'840211',DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 11 DAY),7,DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 12 DAY)),
(2,'SW20261001000002',5,1,1,1,'招牌卤菜粉套餐',1,1280,1280,2,'840212',DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 10 DAY),7,DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 12 DAY)),
(3,'SW20261001000003',6,1,1,1,'招牌卤菜粉套餐',2,1280,2560,2,'840213',DATE_SUB(NOW(),INTERVAL 9 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY),7,DATE_SUB(NOW(),INTERVAL 9 DAY),DATE_SUB(NOW(),INTERVAL 9 DAY)),
(4,'SW20261001000004',4,3,3,2,'双人下午茶',1,8800,8800,2,'932101',DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY),11,DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY)),
(5,'SW20261001000005',8,3,3,2,'双人下午茶',1,8800,8800,2,'932102',DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY),11,DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY)),
(6,'SW20261001000006',9,4,3,2,'单人美式',2,1900,3800,2,'932103',DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 5 DAY),11,DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY)),
(7,'SW20261001000007',10,5,5,3,'四人漓江鱼宴',1,29800,29800,2,'751301',DATE_SUB(NOW(),INTERVAL 10 DAY),DATE_SUB(NOW(),INTERVAL 9 DAY),2,DATE_SUB(NOW(),INTERVAL 10 DAY),DATE_SUB(NOW(),INTERVAL 10 DAY)),
(8,'SW20261001000008',6,5,5,3,'四人漓江鱼宴',1,29800,29800,2,'751302',DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY),2,DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY)),
(9,'SW20261001000009',12,5,5,3,'四人漓江鱼宴',1,29800,29800,2,'751303',DATE_SUB(NOW(),INTERVAL 5 DAY),DATE_SUB(NOW(),INTERVAL 4 DAY),2,DATE_SUB(NOW(),INTERVAL 5 DAY),DATE_SUB(NOW(),INTERVAL 5 DAY)),
(10,'SW20261001000010',4,6,7,1,'涮羊肉双人餐',1,15800,15800,2,'663401',DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY),7,DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY)),
(11,'SW20261001000011',5,6,7,1,'涮羊肉双人餐',1,15800,15800,2,'663402',DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 4 DAY),7,DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY)),
(12,'SW20261001000012',8,6,7,1,'涮羊肉双人餐',1,15800,15800,2,'663403',DATE_SUB(NOW(),INTERVAL 4 DAY),DATE_SUB(NOW(),INTERVAL 3 DAY),7,DATE_SUB(NOW(),INTERVAL 4 DAY),DATE_SUB(NOW(),INTERVAL 4 DAY));

INSERT INTO `tb_payment` (`pay_no`,`order_id`,`channel`,`amount`,`status`,`external_no`,`notify_time`,`create_time`) VALUES
('PAY20261001000001',1,1,1280,1,'MOCK20261001000001',DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 12 DAY)),
('PAY20261001000002',2,1,1280,1,'MOCK20261001000002',DATE_SUB(NOW(),INTERVAL 12 DAY),DATE_SUB(NOW(),INTERVAL 12 DAY)),
('PAY20261001000003',3,0,2560,1,'BAL20261001000003',DATE_SUB(NOW(),INTERVAL 9 DAY),DATE_SUB(NOW(),INTERVAL 9 DAY)),
('PAY20261001000004',4,1,8800,1,'MOCK20261001000004',DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY)),
('PAY20261001000005',5,1,8800,1,'MOCK20261001000005',DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY)),
('PAY20261001000006',6,1,3800,1,'MOCK20261001000006',DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY)),
('PAY20261001000007',7,1,29800,1,'MOCK20261001000007',DATE_SUB(NOW(),INTERVAL 10 DAY),DATE_SUB(NOW(),INTERVAL 10 DAY)),
('PAY20261001000008',8,1,29800,1,'MOCK20261001000008',DATE_SUB(NOW(),INTERVAL 8 DAY),DATE_SUB(NOW(),INTERVAL 8 DAY)),
('PAY20261001000009',9,1,29800,1,'MOCK20261001000009',DATE_SUB(NOW(),INTERVAL 5 DAY),DATE_SUB(NOW(),INTERVAL 5 DAY)),
('PAY20261001000010',10,1,15800,1,'MOCK20261001000010',DATE_SUB(NOW(),INTERVAL 7 DAY),DATE_SUB(NOW(),INTERVAL 7 DAY)),
('PAY20261001000011',11,0,15800,1,'BAL20261001000011',DATE_SUB(NOW(),INTERVAL 6 DAY),DATE_SUB(NOW(),INTERVAL 6 DAY)),
('PAY20261001000012',12,1,15800,1,'MOCK20261001000012',DATE_SUB(NOW(),INTERVAL 4 DAY),DATE_SUB(NOW(),INTERVAL 4 DAY));

INSERT INTO `tb_verify_record` (`order_id`,`verify_code`,`shop_id`,`staff_id`,`amount`,`create_time`)
SELECT `id`,`verify_code`,`shop_id`,`verify_staff_id`,`pay_amount`,`verify_time` FROM `tb_order` WHERE `status`=2;

-- ------------------------------------------------------------
-- 评价（12 条，分数与 tb_shop.rating / tb_shop_rating 严格对齐）
-- 门店1：5,5,4 → 4.7
-- 门店2：无
-- 门店3：5,4,4 → 4.3
-- 门店5：5,4,3 → 4.0
-- 门店7：5,4,3 → 4.0
-- ------------------------------------------------------------
INSERT INTO `tb_review` (`order_id`,`shop_id`,`user_id`,`score_total`,`score_taste`,`score_env`,`score_service`,`content`,`images`,`tags`,`useful_count`,`status`,`create_time`) VALUES
(1,1,4,5,5,4,4,'卤水是真的香，粉很弹。早上七点半到的，前面排了六个人，出餐挺快。','/imgs/review/r1-1.jpg,/imgs/review/r1-2.jpg','1,2,11',23,1,DATE_SUB(NOW(),INTERVAL 11 DAY)),
(2,1,5,5,5,4,5,'加了一份锅烧，脆得很。豆浆是现磨的，不是粉冲的。','/imgs/review/r2-1.jpg','1,3,11',8,1,DATE_SUB(NOW(),INTERVAL 10 DAY)),
(3,1,6,4,4,3,4,'味道没得说，就是环境一般，桌子有点挤，赶时间的可以来。','','1,10',5,1,DATE_SUB(NOW(),INTERVAL 8 DAY)),
(4,3,4,5,5,5,4,'东西巷这家安静，手冲豆子换得勤。提拉米苏偏甜一点。','/imgs/review/r3-1.jpg,/imgs/review/r3-2.jpg','4,12',31,1,DATE_SUB(NOW(),INTERVAL 7 DAY)),
(5,3,8,4,4,5,4,'适合带电脑来坐一下午，插座够。咖啡中规中矩，价格偏上。','','4,13,8',12,1,DATE_SUB(NOW(),INTERVAL 6 DAY)),
(6,3,9,4,3,5,4,'环境是真好，拍照很出片。美式略淡，可能是我口重。','/imgs/review/r4-1.jpg','4',19,1,DATE_SUB(NOW(),INTERVAL 5 DAY)),
(7,5,10,5,5,4,4,'啤酒鱼正宗，田螺酿给得实在。分量四个人吃刚好。','/imgs/review/r5-1.jpg','1,2',44,1,DATE_SUB(NOW(),INTERVAL 9 DAY)),
(8,5,6,4,4,4,4,'味道可以，就是周末人多，等了半小时才有位。','','7',6,1,DATE_SUB(NOW(),INTERVAL 6 DAY)),
(9,5,12,3,3,2,3,'鱼有点咸了，环境一般，性价比不高。','','8,9',3,1,DATE_SUB(NOW(),INTERVAL 3 DAY)),
(10,7,4,5,5,4,4,'羊肉很新鲜，涮完不膻。麻酱给得大方。','/imgs/review/r6-1.jpg','1,2',27,1,DATE_SUB(NOW(),INTERVAL 6 DAY)),
(11,7,5,4,4,3,4,'味道不错，店里有点吵，说话要大声。','','10',4,1,DATE_SUB(NOW(),INTERVAL 4 DAY)),
(12,7,8,3,3,3,3,'锅底一般，价格偏贵，两个人吃了三百多。','','8',2,1,DATE_SUB(NOW(),INTERVAL 3 DAY));

INSERT INTO `tb_review_reply` (`review_id`,`merchant_id`,`staff_id`,`content`,`create_time`) VALUES
(2,1,2,'谢谢支持！豆浆每天六点半现磨，欢迎再来。',DATE_SUB(NOW(),INTERVAL 9 DAY)),
(9,3,2,'非常抱歉让您吃咸了，已经跟后厨反馈，下次来给您调一下口味。',DATE_SUB(NOW(),INTERVAL 2 DAY));

INSERT INTO `tb_review_useful` (`review_id`,`user_id`) VALUES
(1,5),(1,6),(1,7),(1,8),(4,5),(4,6),(4,7),(7,4),(7,5),(7,6),(10,5),(10,6);

INSERT INTO `tb_shop_rating` (`shop_id`,`review_count`,`avg_total`,`avg_taste`,`avg_env`,`avg_service`,`star1`,`star2`,`star3`,`star4`,`star5`) VALUES
(1,3,4.67,4.67,3.67,4.33,0,0,0,1,2),
(2,0,0.00,0.00,0.00,0.00,0,0,0,0,0),
(3,3,4.33,4.00,5.00,4.00,0,0,0,2,1),
(4,0,0.00,0.00,0.00,0.00,0,0,0,0,0),
(5,3,4.00,4.00,3.33,3.67,0,0,1,1,1),
(6,0,0.00,0.00,0.00,0.00,0,0,0,0,0),
(7,3,4.00,4.00,3.33,3.67,0,0,1,1,1),
(8,0,0.00,0.00,0.00,0.00,0,0,0,0,0);

-- ------------------------------------------------------------
-- 笔记
-- ------------------------------------------------------------
INSERT INTO `tb_blog` (`id`,`shop_id`,`user_id`,`title`,`images`,`content`,`liked`,`comments`,`status`,`create_time`) VALUES
(1,1,4,'桂林人的早餐，从一碗卤菜粉开始','/imgs/blog/b1-1.jpg,/imgs/blog/b1-2.jpg','七点半到店，前面已经排了六个人。卤水是那种带点回甘的，锅烧要单独加，脆得掉渣。本地人加粉是不加钱的，直接说「加粉」就行。',86,12,1,DATE_SUB(NOW(),INTERVAL 11 DAY)),
(2,3,9,'东西巷里这家咖啡馆，适合坐一下午','/imgs/blog/b2-1.jpg','靠窗位置能看到青砖墙。手冲豆子每周换，问店员要当日豆单。插座多，带电脑来办公不尴尬。',54,8,1,DATE_SUB(NOW(),INTERVAL 7 DAY)),
(3,5,10,'带外地朋友吃鱼，我一般来这家','/imgs/blog/b3-1.jpg,/imgs/blog/b3-2.jpg','啤酒鱼要选剑骨鱼，刺少。田螺酿是手工塞的，能吃出来。四个人一只鱼两个菜刚好。',132,21,1,DATE_SUB(NOW(),INTERVAL 9 DAY));

INSERT INTO `tb_blog_like` (`blog_id`,`user_id`) VALUES
(1,5),(1,6),(1,7),(1,8),(2,4),(2,11),(3,4),(3,5),(3,6),(3,12);

INSERT INTO `tb_blog_comments` (`blog_id`,`user_id`,`parent_id`,`answer_id`,`content`,`liked`,`status`,`create_time`) VALUES
(1,7,0,0,'加粉真的不加钱吗？下次去试试',12,1,DATE_SUB(NOW(),INTERVAL 10 DAY)),
(1,4,1,1,'真的，直接跟老板说加粉',5,1,DATE_SUB(NOW(),INTERVAL 10 DAY)),
(2,11,0,0,'请问有冷萃吗',3,1,DATE_SUB(NOW(),INTERVAL 6 DAY)),
(3,6,0,0,'啤酒鱼会不会很辣',7,1,DATE_SUB(NOW(),INTERVAL 8 DAY));

INSERT INTO `tb_follow` (`user_id`,`follow_user_id`) VALUES
(4,10),(5,4),(6,4),(7,4),(8,10),(9,4),(11,9),(12,10);

-- ------------------------------------------------------------
-- 通知与统计
-- ------------------------------------------------------------
INSERT INTO `tb_notification` (`user_id`,`type`,`title`,`content`,`biz_id`,`read_flag`,`create_time`) VALUES
(2,2,'收到一条新评价','老四样米粉（七星路店）收到 4 星评价',3,0,DATE_SUB(NOW(),INTERVAL 8 DAY)),
(3,2,'收到一条新评价','漓江渔火（象山店）收到 3 星评价，建议尽快回复',9,0,DATE_SUB(NOW(),INTERVAL 3 DAY)),
(4,1,'订单已核销','您的「招牌卤菜粉套餐」已于到店核销',1,1,DATE_SUB(NOW(),INTERVAL 11 DAY));

INSERT INTO `tb_shop_stats_daily` (`shop_id`,`stat_date`,`view_count`,`order_count`,`verify_count`,`gmv`,`review_count`,`avg_rating`)
SELECT 1, DATE_SUB(CURDATE(), INTERVAL n DAY), 300+n*20, 20+n, 18+n, 23040+n*1280, 0, 4.7
FROM (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) t;

-- ------------------------------------------------------------
-- 校对：把 tb_shop.rating 按 tb_shop_rating 回填一次
-- ------------------------------------------------------------
UPDATE `tb_shop` s
JOIN `tb_shop_rating` r ON r.shop_id = s.id
SET s.rating = ROUND(r.avg_total, 1), s.rating_count = r.review_count;
