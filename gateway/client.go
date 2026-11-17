package main

import (
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// Client 一个 WebSocket 连接。
// 读写各一个 goroutine：gorilla/websocket 不允许并发写，
// 所有写操作必须集中到 writePump 里做，所以用 send channel 串起来。
type Client struct {
	UserID int64
	conn   *websocket.Conn
	send   chan []byte
	hub    *Hub

	// 用 done 而不是 close(send) 来表达关闭：
	// close 之后还能往 channel 发就会 panic，而 Push 可能正持有这个连接的引用。
	// 用 done 通知退出，send 永不关闭，Push 就永远安全（满了走 default）。
	done      chan struct{}
	closeOnce sync.Once
}

func NewClient(hub *Hub, conn *websocket.Conn, userID int64) *Client {
	return &Client{
		UserID: userID,
		conn:   conn,
		send:   make(chan []byte, sendBuf),
		hub:    hub,
		done:   make(chan struct{}),
	}
}

// readPump 只负责读，读完就丢。
// 浏览器几乎不往网关发业务消息，但必须持续读：
// 不读就收不到 pong，60 秒后连接会被判定超时；也感知不到对端主动关闭。
func (c *Client) readPump() {
	defer c.Close()

	c.conn.SetReadLimit(maxMsgSize)
	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	// 每收到一个 pong 就把超时往后推，等于「你还活着」的心跳确认
	c.conn.SetPongHandler(func(string) error {
		return c.conn.SetReadDeadline(time.Now().Add(pongWait))
	})

	for {
		if _, _, err := c.conn.ReadMessage(); err != nil {
			return
		}
	}
}

// writePump 是唯一写这个连接的地方。
func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.Close()
	}()

	for {
		select {
		case msg := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				return
			}
		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		case <-c.done:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			c.conn.WriteMessage(websocket.CloseMessage, []byte{})
			return
		}
	}
}

// Close 可能被 readPump、writePump、Push 三处同时调到，必须幂等。
func (c *Client) Close() {
	c.closeOnce.Do(func() {
		close(c.done)
		c.hub.Unregister(c)
		c.conn.Close()
	})
}
