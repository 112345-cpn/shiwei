package main

import (
	"encoding/json"
	"errors"
	"log/slog"
	"time"

	amqp "github.com/rabbitmq/amqp091-go"
)

// PushMessage 是 Java 侧发到队列里的结构，字段名必须和 Java 的 DTO 一一对应。
//
// 注意 UserID 是数字不是字符串：Java 发 MQ 消息用的是独立的 ObjectMapper
// （没复用 Web 层那个把 Long 转字符串的配置），所以这里按 int64 接。
// 而 HTTP 接口 /api/auth/me 返回的 id 恰恰是字符串 —— 两处不一样，别搞混。
type PushMessage struct {
	UserID int64           `json:"userId"`
	Type   string          `json:"type"`
	Data   json.RawMessage `json:"data"`
}

// outMessage 是最终推给浏览器的结构，前端只要认 type 和 data。
type outMessage struct {
	Type string          `json:"type"`
	Data json.RawMessage `json:"data"`
	Ts   int64           `json:"ts"`
}

// consumePush 连上 MQ 并阻塞消费，直到连接断开才返回。
// 断开由调用方重连，所以这里必须把旧连接关干净再返回。
func consumePush(hub *Hub, cfg Config) error {
	conn, err := amqp.Dial(cfg.MQURL)
	if err != nil {
		return err
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return err
	}
	defer ch.Close()

	// 队列由 Java 侧的 RabbitMQConfig 声明，这里用同样的参数再声明一次是为了幂等：
	// 参数不一致 MQ 会报错，正好能发现两边配置写岔了
	if _, err := ch.QueueDeclare(cfg.PushQueue, true, false, false, false, nil); err != nil {
		return err
	}

	msgs, err := ch.Consume(cfg.PushQueue, "", false, false, false, false, nil)
	if err != nil {
		return err
	}

	slog.Info("已连接 MQ，开始消费推送消息", "queue", cfg.PushQueue)

	// 同步消费：Push 是纯内存操作，不会成为瓶颈。
	// 真到了要并发消费的规模，再换成 worker pool，但现在多一个 goroutine 就是多一处复杂度
	for d := range msgs {
		var m PushMessage
		if err := json.Unmarshal(d.Body, &m); err != nil {
			slog.Warn("推送消息格式不对，丢弃", "err", err)
			// requeue=false：格式错的消息重投多少次都是错的，别堵着队列
			d.Nack(false, false)
			continue
		}

		out, err := json.Marshal(outMessage{
			Type: m.Type,
			Data: m.Data,
			Ts:   time.Now().UnixMilli(),
		})
		if err != nil {
			slog.Warn("组装推送体失败", "err", err)
			d.Nack(false, false)
			continue
		}

		if hub.Push(m.UserID, out) {
			slog.Debug("推送成功", "userId", m.UserID, "type", m.Type)
		} else {
			// 人不在线很正常（关了页面），消息就丢掉。
			// 要保证送达就得做离线消息表，那是另一个复杂度，这里不做
			slog.Debug("用户不在线，跳过", "userId", m.UserID, "type", m.Type)
		}
		d.Ack(false)
	}

	return errors.New("MQ 消费通道已关闭")
}
