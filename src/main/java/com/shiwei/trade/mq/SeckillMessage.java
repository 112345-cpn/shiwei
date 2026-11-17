package com.shiwei.trade.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀下单消息。只带 voucherId + userId：
 * 价格、门店这些落单时才从库里读，不把业务数据塞进消息，
 * 否则券改价后队列里积压的老消息会按旧价下单。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessage implements Serializable {

    private Long voucherId;
    private Long userId;
}
