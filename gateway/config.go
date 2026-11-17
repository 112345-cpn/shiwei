package main

import (
	"os"
	"time"
)

type Config struct {
	Port     string
	JavaBase string
	MQURL    string
	PushQueue string
}

func loadConfig() Config {
	return Config{
		Port:      env("GATEWAY_PORT", "8082"),
		JavaBase:  env("JAVA_BASE_URL", "http://localhost:8081"),
		// 本地开发 MQ 映射在 5673；容器内走服务名 5672
		MQURL:     env("RABBITMQ_URL", "amqp://guest:guest@localhost:5673/"),
		PushQueue: env("PUSH_QUEUE", "shiwei.queue.ws.push"),
	}
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

const (
	// 写超时。网络卡住时不能让 goroutine 一直挂着
	writeWait = 10 * time.Second
	// 读超时 = 多久收不到 pong 就认为连接死了
	pongWait = 60 * time.Second
	// 发 ping 的间隔，必须小于 pongWait，否则还没等对方回就超时了
	pingPeriod = (pongWait * 9) / 10
	// 单条消息上限，防止客户端发超大帧把内存打爆
	maxMsgSize = 512
	// 每个连接的发送缓冲。满了说明对端不读，走丢弃
	sendBuf = 32
)
