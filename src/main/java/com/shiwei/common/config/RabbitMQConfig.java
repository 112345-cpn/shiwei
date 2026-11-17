package com.shiwei.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_SHOP = "shiwei.shop";
    public static final String EXCHANGE_TRADE = "shiwei.trade";
    public static final String EXCHANGE_DELAY = "shiwei.delay";

    public static final String QUEUE_RATING = "shiwei.queue.rating";
    public static final String QUEUE_ORDER_PAID = "shiwei.queue.order.paid";
    public static final String QUEUE_ORDER_TIMEOUT = "shiwei.queue.order.timeout";
    public static final String QUEUE_ORDER_DELAY = "shiwei.queue.order.delay";
    public static final String QUEUE_NOTIFY = "shiwei.queue.notify";

    public static final String RK_RATING = "review.rating";
    public static final String RK_ORDER_PAID = "order.paid";
    public static final String RK_ORDER_TIMEOUT = "order.timeout";
    public static final String RK_NOTIFY = "notify.#";

    public static final long ORDER_PAY_TTL_MS = 15 * 60 * 1000L;

    /**
     * 消息体用 JSON 而不是 Java 序列化：跨语言、可读、版本兼容。
     * 这里刻意用无参构造，不复用 Web 层那个 ObjectMapper ——
     * 那个把 Long 序列化成了字符串，MQ 里再绕回来容易出类型问题。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange shopExchange() {
        return new TopicExchange(EXCHANGE_SHOP, true, false);
    }

    @Bean
    public TopicExchange tradeExchange() {
        return new TopicExchange(EXCHANGE_TRADE, true, false);
    }

    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(EXCHANGE_DELAY, true, false);
    }

    @Bean
    public Queue ratingQueue() {
        return QueueBuilder.durable(QUEUE_RATING).build();
    }

    @Bean
    public Queue orderPaidQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_PAID).build();
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFY).build();
    }

    /**
     * 订单超时队列。靠 TTL + 死信实现延迟，不依赖 rabbitmq_delayed_message_exchange 插件，
     * 这样本地和服务器上都不用额外装东西。
     * 注意 x-message-ttl 是队列级参数，一旦队列已存在就不能改，
     * 调整超时时间要换队列名或删队列重建。
     */
    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_DELAY)
                .withArgument("x-message-ttl", (int) ORDER_PAY_TTL_MS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_TRADE)
                .withArgument("x-dead-letter-routing-key", RK_ORDER_TIMEOUT)
                .build();
    }

    @Bean
    public Queue orderTimeoutConsumerQueue() {
        return QueueBuilder.durable(QUEUE_ORDER_TIMEOUT).build();
    }

    @Bean
    public Binding ratingBinding() {
        return BindingBuilder.bind(ratingQueue()).to(shopExchange()).with(RK_RATING);
    }

    @Bean
    public Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(tradeExchange()).with(RK_ORDER_PAID);
    }

    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(notifyQueue()).to(tradeExchange()).with(RK_NOTIFY);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutConsumerQueue()).to(tradeExchange()).with(RK_ORDER_TIMEOUT);
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(delayExchange()).with(RK_ORDER_TIMEOUT);
    }
}
