package main

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	// 本地开发全放开。上生产必须校验 Origin，否则任意站点都能连你的网关
	CheckOrigin: func(r *http.Request) bool { return true },
}

func main() {
	cfg := loadConfig()
	hub := NewHub()

	mux := http.NewServeMux()
	mux.HandleFunc("/ws", func(w http.ResponseWriter, r *http.Request) { handleWS(hub, cfg, w, r) })
	mux.HandleFunc("/healthz", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("ok"))
	})
	mux.HandleFunc("/stats", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]int{
			"connections": hub.OnlineCount(),
			"users":       hub.UserCount(),
		})
	})

	// MQ 消费放后台，断了就隔 5 秒重连。
	// 这里是死循环，所以必须能在收到退出信号时退出，否则进程关不掉
	mqCtx, mqStop := context.WithCancel(context.Background())
	defer mqStop()
	go func() {
		for {
			if err := consumePush(hub, cfg); err != nil {
				slog.Error("MQ 消费断开，5 秒后重连", "err", err)
			}
			select {
			case <-mqCtx.Done():
				return
			case <-time.After(5 * time.Second):
			}
		}
	}()

	srv := &http.Server{Addr: ":" + cfg.Port, Handler: mux}

	// Ctrl-C 或 docker stop 时 ctx 被取消
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go func() {
		slog.Info("网关启动", "port", cfg.Port, "java", cfg.JavaBase)
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			slog.Error("监听失败", "err", err)
			stop() // 端口被占了，别让主协程一直等信号
		}
	}()

	<-ctx.Done()
	slog.Info("收到退出信号，开始优雅关闭")

	// 先停 MQ 消费，再关 HTTP，最后等连接自然关掉。
	// 顺序反了的话，HTTP 关了但 MQ 还在往里推，消息会丢
	mqStop()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		slog.Error("关闭超时，强制退出", "err", err)
	}
	slog.Info("已退出")
}

func handleWS(hub *Hub, cfg Config, w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	if token == "" {
		http.Error(w, "missing token", http.StatusUnauthorized)
		return
	}

	// 先鉴权再 Upgrade：Upgrade 之后 HTTP 头已经发出去了，没法再返回 401
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
	defer cancel()

	userID, err := verifyToken(ctx, cfg, token)
	if err != nil {
		slog.Warn("鉴权失败", "err", err)
		http.Error(w, "invalid token", http.StatusUnauthorized)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		slog.Warn("协议升级失败", "err", err)
		return
	}

	c := NewClient(hub, conn, userID)
	hub.Register(c)

	slog.Info("连接建立", "userId", userID, "online", hub.OnlineCount())

	// 读写分开跑。writePump 是唯一写连接的地方，readPump 负责感知断开
	go c.writePump()
	go c.readPump()
}
