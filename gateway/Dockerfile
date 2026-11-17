FROM golang:1.23-alpine AS build

WORKDIR /src

# 先只拷依赖清单，这样改代码不会让依赖层缓存失效
COPY go.mod go.sum ./
RUN go mod download

COPY . .
# CGO_ENABLED=0 是必须的：编译出静态二进制，alpine 里不用装 glibc
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/gateway .

FROM alpine:3.20

# 不用 root 跑。挂掉的时候影响面小一点
RUN adduser -D -u 10001 app
USER app

COPY --from=build /out/gateway /gateway

EXPOSE 8082

# 容器停的时候先收到 SIGTERM，main 里的 signal.NotifyContext 靠它做优雅关闭
ENTRYPOINT ["/gateway"]
