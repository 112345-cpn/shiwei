/* ==========================================================================
   拾味 SHIWEI — 接口层
   只做三件事：带 token、统一解包 Result、把 401 变成跳登录。
   页面里不出现任何 fetch 细节。
   ========================================================================== */

const API = (() => {
  const BASE = '/api';
  const TOKEN_KEY = 'shiwei_token';
  const USER_KEY = 'shiwei_user';

  /* ---------------- token ---------------- */

  function token() {
    return localStorage.getItem(TOKEN_KEY) || '';
  }

  function setSession(tk, user) {
    localStorage.setItem(TOKEN_KEY, tk);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  function currentUser() {
    try {
      return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
    } catch (e) {
      return null;
    }
  }

  function setUser(user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  function isLogin() {
    return !!token();
  }

  /* ---------------- 核心请求 ---------------- */

  function toQuery(params) {
    if (!params) return '';
    const parts = [];
    Object.keys(params).forEach((k) => {
      const v = params[k];
      if (v === undefined || v === null || v === '') return;
      parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
    });
    return parts.length ? '?' + parts.join('&') : '';
  }

  async function request(method, path, { params, body, form } = {}) {
    const headers = {};
    const tk = token();
    if (tk) headers['authorization'] = tk;

    let payload;
    if (form) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      payload = new URLSearchParams(form).toString();
    } else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }

    let res;
    try {
      res = await fetch(BASE + path + toQuery(params), {
        method,
        headers,
        body: payload,
      });
    } catch (e) {
      throw new Error('网络异常，检查后端服务是否启动');
    }

    // 登录拦截器抛的未授权
    if (res.status === 401) {
      const back = location.pathname.split('/').pop() + location.search;
      clearSession();
      location.href = 'login.html?back=' + encodeURIComponent(back);
      throw new Error('请先登录');
    }

    let json;
    try {
      json = await res.json();
    } catch (e) {
      throw new Error('服务端返回异常（' + res.status + '）');
    }

    // 后端统一返回 {success, errorMsg, data, total}
    if (json && json.success === false) {
      throw new Error(json.errorMsg || '操作失败');
    }
    return json;
  }

  const get = (path, params) => request('GET', path, { params });
  const post = (path, body, params) => request('POST', path, { body, params });
  const put = (path, body, params) => request('PUT', path, { body, params });
  const postForm = (path, form) => request('POST', path, { form });

  /**
   * 上传单个文件。multipart 不能设 Content-Type，浏览器要自己补 boundary。
   * 返回后端给的文件名，形如 /blogs/3/5/uuid.jpg
   */
  async function upload(path, file) {
    const fd = new FormData();
    fd.append('file', file);

    const headers = {};
    const tk = token();
    if (tk) headers['authorization'] = tk;

    const res = await fetch(BASE + path, { method: 'POST', headers, body: fd });
    if (res.status === 401) {
      clearSession();
      location.href = 'login.html';
      throw new Error('请先登录');
    }
    const json = await res.json();
    if (json && json.success === false) throw new Error(json.errorMsg || '上传失败');
    return json.data;
  }

  /* ---------------- 分页兼容 ----------------
     后端有两种返回：List 直接返回，或 MyBatis-Plus 的 Page 对象。
     页面只想要 { records, total }，在这里抹平差异。

     另外后端把 Long 序列化成了字符串（避免雪花 ID 被 JS 精度截断），
     所以 total 可能是 "23" 而不是 23，统一转成数字。
  --------------------------------------------- */

  function normalizePage(json) {
    const d = json ? json.data : null;
    const total = Number(json && json.total) || 0;
    if (Array.isArray(d)) {
      return { records: d, total: total || d.length };
    }
    if (d && Array.isArray(d.records)) {
      return { records: d.records, total: Number(d.total) || total || d.records.length };
    }
    return { records: [], total: 0 };
  }

  /* ---------------- 业务接口 ---------------- */

  const Shop = {
    types: () => get('/shop-type/list'),
    byType: (typeId, current = 1, sortBy = '', x, y) =>
      get('/shop/of/type', { typeId, current, sortBy, x, y }),
    byName: (name, current = 1) => get('/shop/of/name', { name, current }),
    detail: (id) => get('/shop/' + id),
    vouchers: (shopId) => get('/voucher/list/' + shopId),
    seckill: (voucherId) => post('/voucher-order/seckill/' + voucherId),
  };

  const Blog = {
    hot: (current = 1) => get('/blog/hot', { current }),
    detail: (id) => get('/blog/' + id),
    likes: (id) => get('/blog/likes/' + id),
    mine: (current = 1) => get('/blog/of/me', { current }),
    ofUser: (id, current = 1) => get('/blog/of/user', { id, current }),
    ofFollow: (lastId, offset = 0) => get('/blog/of/follow', { lastId, offset }),
    publish: (blog) => post('/blog', blog),
    like: (id) => put('/blog/like/' + id),
  };

  const User = {
    sendCode: (phone) => postForm('/user/code', { phone }),
    login: (phone, code) => post('/user/login', { phone, code }),
    logout: () => post('/user/logout'),
    me: () => get('/user/me'),
    info: (id) => get('/user/info/' + id),
    signCount: () => get('/user/sign/count'),
  };

  const Follow = {
    follow: (id, isFollow) => put('/follow/' + id + '/' + isFollow),
    isFollow: (id) => get('/follow/or/not/' + id),
    common: (id) => get('/follow/common/' + id),
  };

  /* ---------------- WebSocket ----------------
     连接地址：/ws/notification?token=xxx
     nginx 需要把 /ws 透传成 Upgrade 连接（见 conf/nginx.conf）。
     断线自动重连，退避 1s → 2s → 4s → 8s，封顶 15s。
  --------------------------------------------- */

  const ws = {
    socket: null,
    retry: 0,
    timer: null,
    handlers: [],
    manualClose: false,

    on(fn) {
      this.handlers.push(fn);
      return () => {
        this.handlers = this.handlers.filter((h) => h !== fn);
      };
    },

    connect() {
      if (!isLogin()) return;
      if (this.socket && (this.socket.readyState === 0 || this.socket.readyState === 1)) return;

      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      const url = proto + '//' + location.host + '/ws/notification?token=' + encodeURIComponent(token());

      let socket;
      try {
        socket = new WebSocket(url);
      } catch (e) {
        this.scheduleReconnect();
        return;
      }
      this.socket = socket;

      socket.onopen = () => {
        this.retry = 0;
        this.emit({ type: '_ws_open' });
      };

      socket.onmessage = (ev) => {
        let msg;
        try {
          msg = JSON.parse(ev.data);
        } catch (e) {
          return;
        }
        this.emit(msg);
      };

      socket.onclose = () => {
        this.emit({ type: '_ws_close' });
        if (!this.manualClose) this.scheduleReconnect();
      };

      socket.onerror = () => {
        /* onclose 一定会跟着来，这里不用重复处理 */
      };
    },

    scheduleReconnect() {
      clearTimeout(this.timer);
      const wait = Math.min(1000 * Math.pow(2, this.retry), 15000);
      this.retry += 1;
      this.timer = setTimeout(() => this.connect(), wait);
    },

    emit(msg) {
      this.handlers.forEach((fn) => {
        try {
          fn(msg);
        } catch (e) {
          console.error('[ws handler]', e);
        }
      });
    },

    close() {
      this.manualClose = true;
      clearTimeout(this.timer);
      if (this.socket) this.socket.close();
      this.socket = null;
    },
  };

  return {
    token,
    isLogin,
    setSession,
    setUser,
    currentUser,
    clearSession,
    normalizePage,
    get,
    post,
    put,
    postForm,
    upload,
    Shop,
    Blog,
    User,
    Follow,
    ws,
  };
})();
