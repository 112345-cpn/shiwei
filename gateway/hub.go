package main

import "sync"

// Hub 维护 userId → 连接列表。
// 用切片而不是单个连接：同一个账号可能手机和电脑同时在线。
type Hub struct {
	mu      sync.RWMutex
	clients map[int64][]*Client
}

func NewHub() *Hub {
	return &Hub{clients: make(map[int64][]*Client)}
}

func (h *Hub) Register(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.clients[c.UserID] = append(h.clients[c.UserID], c)
}

func (h *Hub) Unregister(c *Client) {
	h.mu.Lock()
	defer h.mu.Unlock()

	list := h.clients[c.UserID]
	for i, v := range list {
		if v == c {
			h.clients[c.UserID] = append(list[:i], list[i+1:]...)
			break
		}
	}
	// 这个人一个连接都不剩了就删 key，否则 map 会一直变大
	if len(h.clients[c.UserID]) == 0 {
		delete(h.clients, c.UserID)
	}
}

// Push 把消息推给某个用户的所有在线连接。
// 锁只包住读 map 那一步：后面遍历的是切片副本，
// 每个连接的操作互不影响，持锁时间越短越好。
func (h *Hub) Push(userID int64, msg []byte) bool {
	h.mu.RLock()
	conns := h.clients[userID]
	h.mu.RUnlock()

	if len(conns) == 0 {
		return false
	}

	sent := false
	for _, c := range conns {
		select {
		case c.send <- msg:
			sent = true
		default:
			// 缓冲满了 = 这个连接在对端堵着。踢掉它，
			// 不能为了一个慢客户端把整个消费协程卡住
			c.Close()
		}
	}
	return sent
}

func (h *Hub) OnlineCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()

	total := 0
	for _, v := range h.clients {
		total += len(v)
	}
	return total
}

func (h *Hub) UserCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return len(h.clients)
}
