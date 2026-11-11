/* ==========================================================================
   拾味 SHIWEI — 公共 UI
   顶部导航、Toast、图片兜底、格式化、秒杀结果弹层。
   ========================================================================== */

const UI = (() => {
  /* ---------------- 图标（内联 SVG，免额外请求） ---------------- */

  const icons = {
    search:
      '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.6-3.6"/></svg>',
    star: '<svg width="11" height="11" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2.6l2.9 6.1 6.6.9-4.8 4.7 1.2 6.6-5.9-3.2-5.9 3.2 1.2-6.6L2.5 9.6l6.6-.9z"/></svg>',
    heart:
      '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M20.8 6.6a5.4 5.4 0 0 0-7.7 0L12 7.7l-1.1-1.1a5.4 5.4 0 0 0-7.7 7.7L12 22l8.8-7.7a5.4 5.4 0 0 0 0-7.7z"/></svg>',
    heartFill:
      '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21.4l-1.1-1a140 140 0 0 1-5.6-5.2C3.4 13.3 2.4 11.6 2.4 9.6A5.6 5.6 0 0 1 8 4c1.6 0 3 .8 4 2 1-1.2 2.4-2 4-2a5.6 5.6 0 0 1 5.6 5.6c0 2-1 3.7-2.9 5.6a140 140 0 0 1-5.6 5.2z"/></svg>',
    pin: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z"/><circle cx="12" cy="10" r="2.6"/></svg>',
    clock:
      '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5.4l3.4 2"/></svg>',
    fire: '<svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M13 2s.8 4-2.2 6.6C8 11 5 12.6 5 16a7 7 0 0 0 14 0c0-3-1.6-5.4-3.4-7.2-.6 1.4-1.4 2-2.2 2 .6-3-.4-6.4-.4-8.8z"/></svg>',
    check:
      '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="m4 12.6 5.2 5.2L20 6.4"/></svg>',
    close:
      '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>',
    empty:
      '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 8.5 12 4l9 4.5-9 4.5z"/><path d="M3 12.5 12 17l9-4.5"/><path d="M3 16.5 12 21l9-4.5"/></svg>',
    plus: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  };

  /* ---------------- 工具 ---------------- */

  function esc(s) {
    if (s === null || s === undefined) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /** 分转元：4750 → 47.5 */
  function yuan(fen) {
    if (fen === null || fen === undefined || fen === '') return '0';
    const n = Number(fen) / 100;
    return Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0$/, '');
  }

  /** 评分：37 → 3.7 */
  function score(n) {
    if (!n && n !== 0) return '—';
    return (Number(n) / 10).toFixed(1);
  }

  /** 销量：4215 → 4215，26891 → 2.7万 */
  function sold(n) {
    const v = Number(n) || 0;
    if (v >= 10000) return (v / 10000).toFixed(1).replace(/\.0$/, '') + '万';
    return String(v);
  }

  /** 取第一张图，过滤空段 */
  function firstImage(images) {
    if (!images) return '';
    const list = String(images)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    return list[0] || '';
  }

  function imageList(images) {
    if (!images) return [];
    return String(images)
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
  }

  /** 时间：兼容 "2026-11-07 18:10:39" / ISO / 数组 */
  function parseTime(v) {
    if (!v) return null;
    if (Array.isArray(v)) {
      return new Date(v[0], (v[1] || 1) - 1, v[2] || 1, v[3] || 0, v[4] || 0, v[5] || 0);
    }
    const d = new Date(String(v).replace(' ', 'T'));
    return isNaN(d.getTime()) ? null : d;
  }

  function timeAgo(v) {
    const d = parseTime(v);
    if (!d) return '';
    const diff = (Date.now() - d.getTime()) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
    if (diff < 86400 * 30) return Math.floor(diff / 86400) + ' 天前';
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  function pad(n) {
    return n < 10 ? '0' + n : String(n);
  }

  /** 分类图标：/types/ms.png → imgs/types/ms.png */
  function typeIcon(icon) {
    if (!icon) return '';
    if (/^https?:/.test(icon)) return icon;
    const file = icon.split('/').pop();
    return 'imgs/types/' + file;
  }

  const DEFAULT_AVATAR = 'imgs/icons/default-icon.png';

  /* ---------------- Toast ---------------- */

  let toastBox;

  function toast(message, type = '') {
    if (!toastBox) {
      toastBox = document.createElement('div');
      toastBox.className = 'toasts';
      document.body.appendChild(toastBox);
    }
    const el = document.createElement('div');
    el.className = 'toast' + (type ? ' toast--' + type : '');
    el.textContent = message;
    toastBox.appendChild(el);
    setTimeout(() => {
      el.classList.add('is-out');
      setTimeout(() => el.remove(), 220);
    }, 2200);
  }

  /* ---------------- 图片兜底 ----------------
     shop.images 是外链 CDN，可能因为网络或防盗链挂掉。
     用 no-referrer 提高成功率；真挂了就换成首字渐变块，别显示破图。
  --------------------------------------------- */

  function imgOrPlaceholder(url, alt, cls = '', ratioClass = '') {
    const text = esc((alt || '?').slice(0, 1));
    if (!url) {
      return '<div class="' + ratioClass + ' shop__ph">' + text + '</div>';
    }
    return (
      '<img class="' + cls + '" src="' + esc(url) + '" alt="' + esc(alt || '') +
      '" loading="lazy" referrerpolicy="no-referrer" ' +
      "onerror=\"this.outerHTML='<div class=\\\"" + ratioClass + " shop__ph\\\">" + text + "</div>'\" />"
    );
  }

  /* ---------------- 顶部导航 ---------------- */

  function header(active) {
    const user = API.currentUser();
    const links = [
      { key: 'home', href: 'index.html', label: '发现' },
      { key: 'notes', href: 'notes.html', label: '笔记' },
      { key: 'me', href: 'me.html', label: '我的' },
    ];

    const nav = links
      .map(
        (l) =>
          '<a href="' + l.href + '" class="' + (active === l.key ? 'is-on' : '') + '">' +
          l.label +
          '</a>'
      )
      .join('');

    const right = user
      ? '<a href="me.html" class="row" title="' + esc(user.nickName || '') + '">' +
        '<img class="avatar" src="' + esc(user.icon || DEFAULT_AVATAR) +
        '" alt="头像" onerror="this.src=\'' + DEFAULT_AVATAR + '\'" /></a>'
      : '<a href="login.html" class="btn btn--dark btn--sm">登录</a>';

    return (
      '<header class="hd" id="hd"><div class="wrap hd__inner">' +
      '<a class="brand" href="index.html">' +
      '<span class="brand__mark">拾</span>' +
      '<span class="brand__name">拾味<em>·</em>SHIWEI</span>' +
      '</a>' +
      '<nav class="nav">' + nav + '</nav>' +
      '<div class="hd__spacer"></div>' +
      '<form class="search" onsubmit="return UI.onSearch(event)">' +
      icons.search +
      '<input id="hd-search" type="search" placeholder="搜店名，比如「火锅」" autocomplete="off" />' +
      '</form>' +
      '<div class="hd__user">' + right + '</div>' +
      '</div></header>'
    );
  }

  function onSearch(e) {
    e.preventDefault();
    const kw = (document.getElementById('hd-search').value || '').trim();
    if (!kw) return false;
    location.href = 'index.html?kw=' + encodeURIComponent(kw);
    return false;
  }

  function mountHeader(active) {
    const holder = document.getElementById('app-header');
    if (!holder) return;
    holder.innerHTML = header(active);

    // 滚动后给导航加分隔线
    const hd = document.getElementById('hd');
    const onScroll = () => hd.classList.toggle('is-stuck', window.scrollY > 8);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  /* ---------------- 页脚 ---------------- */

  function mountFooter() {
    const holder = document.getElementById('app-footer');
    if (!holder) return;
    holder.innerHTML =
      '<footer class="ft"><div class="wrap ft__inner">' +
      '<div>拾味 SHIWEI · 发现城市里值得一去的地方</div>' +
      '<div class="ft__links">' +
      '<a href="index.html">发现</a><a href="notes.html">笔记</a><a href="me.html">我的</a>' +
      '</div></div></footer>';
  }

  /* ---------------- 骨架屏 ---------------- */

  function skeletonGrid(n = 6) {
    let out = '';
    for (let i = 0; i < n; i++) {
      out +=
        '<div class="sk-card"><div class="sk sk-card__media"></div>' +
        '<div class="sk-card__body"><div class="sk sk-line" style="width:62%"></div>' +
        '<div class="sk sk-line" style="width:38%"></div></div></div>';
    }
    return out;
  }

  function empty(title, desc) {
    return (
      '<div class="empty"><div class="empty__icon">' + icons.empty + '</div>' +
      '<p class="empty__title">' + esc(title || '什么都没有') + '</p>' +
      (desc ? '<p class="empty__desc">' + esc(desc) + '</p>' : '') +
      '</div>'
    );
  }

  /* ---------------- 秒杀结果弹层 ---------------- */

  function sheet({ ok, title, desc, orderId, actionText, onAction }) {
    const mask = document.createElement('div');
    mask.className = 'sheet-mask';
    mask.innerHTML =
      '<div class="sheet">' +
      '<div class="sheet__badge ' + (ok ? 'sheet__badge--ok' : '') + '">' +
      (ok ? icons.check : icons.close) +
      '</div>' +
      '<h3 class="sheet__title">' + esc(title) + '</h3>' +
      '<p class="sheet__desc">' + esc(desc) + '</p>' +
      (orderId ? '<p class="sheet__order">订单号 ' + esc(orderId) + '</p>' : '') +
      '<button class="btn btn--dark btn--block" id="sheet-ok">' +
      esc(actionText || '知道了') +
      '</button>' +
      '</div>';

    document.body.appendChild(mask);
    const close = () => mask.remove();
    mask.querySelector('#sheet-ok').onclick = () => {
      close();
      if (onAction) onAction();
    };
    mask.onclick = (e) => {
      if (e.target === mask) close();
    };
    return close;
  }

  /* ---------------- 秒杀结果统一转发 ----------------
     任何页面只要调 UI.bindSeckillPush(handler)，就能收到后端的
     {type:'seckill_result', orderId, voucherId, success, msg}。
     同时把连接状态暴露到 window.__wsState，方便调试。
  --------------------------------------------- */

  function bindSeckillPush(handler) {
    API.ws.on((msg) => {
      if (msg.type === '_ws_open') {
        window.__wsState = 'open';
      } else if (msg.type === '_ws_close') {
        window.__wsState = 'closed';
      } else if (msg.type === 'seckill_result') {
        handler(msg);
      }
    });
    API.ws.connect();
  }

  /* ---------------- 登录守卫 ---------------- */

  function requireLogin() {
    if (API.isLogin()) return true;
    const back = location.pathname.split('/').pop() + location.search;
    location.href = 'login.html?back=' + encodeURIComponent(back);
    return false;
  }

  /* ---------------- URL 参数 ---------------- */

  function param(name) {
    return new URLSearchParams(location.search).get(name) || '';
  }

  return {
    icons,
    esc,
    yuan,
    score,
    sold,
    firstImage,
    imageList,
    parseTime,
    timeAgo,
    typeIcon,
    DEFAULT_AVATAR,
    toast,
    imgOrPlaceholder,
    header,
    mountHeader,
    mountFooter,
    onSearch,
    skeletonGrid,
    empty,
    sheet,
    bindSeckillPush,
    requireLogin,
    param,
  };
})();
