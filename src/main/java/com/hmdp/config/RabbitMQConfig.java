package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 *
 * 队列规划：
 * 1. hmdp.seckill.order   ：秒杀下单队列（核心队列）
 * 2. hmdp.seckill.order.dl ：秒杀下单死信队列（订单超时关单）
 * 3. hmdp.voucher          ：优惠券通用队列（预留）
 *
 * 交换机规划：
 * 1. hmdp.seckill.exchange  ：秒杀 Direct 交换机
 *
 * @author HM-DianPing
 */
@Configuration
public class RabbitMQConfig {

    /** ========== 秒杀下单相关队列 ========== */

    /** 秒杀订单队列 */
    public static final String SECKILL_ORDER_QUEUE = "hmdp.seckill.order";

    /** 秒杀订单死信队列（延迟关单） */
    public static final String SECKILL_ORDER_DL_QUEUE = "hmdp.seckill.order.dl";

    /** 延迟队列：无消费者，靠队列级 TTL 到期后转入死信队列 */
    public static final String SECKILL_DELAY_QUEUE = "hmdp.seckill.order.delay";

    /** 秒杀交换机 */
    public static final String SECKILL_EXCHANGE = "hmdp.seckill.exchange";

    /** 秒杀订单路由键 */
    public static final String SECKILL_ORDER_RK = "seckill.order";

    /** 延迟队列路由键 */
    public static final String SECKILL_DELAY_RK = "seckill.delay";

    /** 死信路由键（延迟队列过期后转发到死信队列） */
    public static final String SECKILL_DL_RK = "seckill.order.dl";

    /** 未支付自动关单的延迟时长（毫秒） */
    public static final int ORDER_TIMEOUT_MS = 30000;

    /** ========== Bean 定义 ========== */

    /**
     * JSON 消息转换器（生产环境主流方案，跨语言兼容）
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置（开启发送回调和消息可达性回调）
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // 开启 mandatory 触发回调，处理路由不可达的消息
        template.setMandatory(true);
        return template;
    }

    /**
     * 秒杀订单队列（核心队列）
     *
     * 配置说明：
     * - x-dead-letter-exchange：当消息被 reject/expired 时，转发给死信交换机
     * - x-dead-letter-routing-key：死信路由键
     * - x-message-ttl：消息级 TTL（本场景未设置，使用消息级 TTL 控制延迟）
     */
    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new HashMap<>(2);
        // 消息过期后，转发到死信交换机
        args.put("x-dead-letter-exchange", SECKILL_EXCHANGE);
        args.put("x-dead-letter-routing-key", "seckill.order.dl");
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE)
                .withArguments(args)
                .build();
    }

    /**
     * 秒杀订单死信队列（处理超时关单）
     */
    @Bean
    public Queue seckillOrderDeadLetterQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_DL_QUEUE).build();
    }

    /**
     * 延迟队列（实现 30s 未支付自动关单）
     *
     * 关键点：这个队列【故意不配消费者】。
     * 消息进来后没人取，就静静等到 x-message-ttl 到期，然后 RabbitMQ 把它
     * 按 x-dead-letter-exchange / x-dead-letter-routing-key 转到死信队列，
     * 死信队列的消费者再执行「关单 + 库存回补」。
     *
     * 之前把 TTL 设在「下单消息」上是行不通的：那条消息会被下单消费者立刻取走，
     * 根本等不到过期，死信队列永远收不到消息。
     */
    @Bean
    public Queue seckillDelayQueue() {
        Map<String, Object> args = new HashMap<>(3);
        args.put("x-dead-letter-exchange", SECKILL_EXCHANGE);
        args.put("x-dead-letter-routing-key", SECKILL_DL_RK);
        args.put("x-message-ttl", ORDER_TIMEOUT_MS);
        return QueueBuilder.durable(SECKILL_DELAY_QUEUE)
                .withArguments(args)
                .build();
    }

    /**
     * 秒杀交换机（Direct 类型，根据路由键分发）
     */
    @Bean
    public DirectExchange seckillExchange() {
        return ExchangeBuilder.directExchange(SECKILL_EXCHANGE).durable(true).build();
    }

    /**
     * 绑定：秒杀订单队列
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillExchange())
                .with(SECKILL_ORDER_RK);
    }

    /**
     * 绑定：秒杀订单死信队列
     */
    @Bean
    public Binding seckillOrderDeadLetterBinding() {
        return BindingBuilder.bind(seckillOrderDeadLetterQueue())
                .to(seckillExchange())
                .with("seckill.order.dl");
    }

    /**
     * 绑定：延迟队列
     */
    @Bean
    public Binding seckillDelayBinding() {
        return BindingBuilder.bind(seckillDelayQueue())
                .to(seckillExchange())
                .with(SECKILL_DELAY_RK);
    }
}