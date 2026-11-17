package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
)

// apiResult 对应 Java 侧的 Result<T>
type apiResult struct {
	Code int             `json:"code"`
	Msg  string          `json:"msg"`
	Data json.RawMessage `json:"data"`
}

// userVO 对应 Java 侧的 UserVO。
// 注意 id 是字符串：Java 那边配了 ToStringSerializer，把所有 Long 转成了字符串
// （因为 JS 的 Number 存不下 64 位整数）。这里用 string 接再手动转，别直接用 int64。
type userVO struct {
	Id       string `json:"id"`
	NickName string `json:"nickName"`
	Role     int    `json:"role"`
}

// verifyToken 拿 token 去问 Java「这是谁」。
// 网关自己不存会话，鉴权只有一处真相，转服务也没关系。
func verifyToken(ctx context.Context, cfg Config, token string) (int64, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, cfg.JavaBase+"/api/auth/me", nil)
	if err != nil {
		return 0, err
	}
	// Java 的 AuthInterceptor 从 authorization 头取 token
	req.Header.Set("authorization", token)

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return 0, fmt.Errorf("鉴权接口返回 %d", resp.StatusCode)
	}

	var r apiResult
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return 0, err
	}
	if r.Code != 0 {
		return 0, errors.New("token 无效：" + r.Msg)
	}

	var u userVO
	if err := json.Unmarshal(r.Data, &u); err != nil {
		return 0, err
	}
	if u.Id == "" {
		return 0, errors.New("鉴权接口没返回用户 id")
	}

	return strconv.ParseInt(u.Id, 10, 64)
}
