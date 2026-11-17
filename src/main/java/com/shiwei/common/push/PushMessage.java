package com.shiwei.common.push;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 发给 Go 网关的消息。
 * userId 在这里是数字而不是字符串 —— MQ 用的是 RabbitMQConfig 里那个独立 ObjectMapper，
 * 没有挂 Web 层的 ToStringSerializer。Go 侧按 int64 接。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushMessage implements Serializable {

    private Long userId;
    private String type;
    private Object data;
}
