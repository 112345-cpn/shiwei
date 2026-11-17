module shiwei/gateway

// 只用到 slog（1.21+）和标准库，降到 1.23 是为了让 docker 镜像用 golang:1.23-alpine，
// 这个 tag 一定存在；写 1.26 可能拉不到对应镜像
go 1.23

require (
	github.com/gorilla/websocket v1.5.3
	github.com/rabbitmq/amqp091-go v1.17.0
)
