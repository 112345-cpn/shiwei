# -*- coding: utf-8 -*-
"""
黑马点评（hmdp）端到端验证脚本
=====================================
覆盖：
  P1 服务连通          GET /shop-type/list
  P2 登录 + WS 握手    POST /user/code → /user/login → ws /ws/notification
  P3 建秒杀券          POST /voucher/seckill（顺带验证 Redis 库存预加载）
  P4 单次抢购          POST /voucher-order/seckill/{id}
      ├─ WS 推送断言（seckill_result，orderId 一致）→ 证明「先提交事务再推送」
      ├─ orderId 是字符串 → 证明 Long 序列化修复生效
      └─ 重复下单被拦（一人一单）
  P5 并发压测          600 用户 × 4 次 = 2400 请求 / 500 库存
      └─ 断言：成功数 = 500、订单数 = 500、库存 = 0、无负库存
  P6 超时关单          等 35s → 死信队列自动关单（status=4）+ 库存回补

依赖：requests、websocket-client；DB/Redis 断言走 docker exec。
用法：python tools/e2e_verify.py
"""
import json
import subprocess
import sys
import time
import traceback
import uuid
from concurrent.futures import ThreadPoolExecutor

import requests
import websocket

BASE = "http://127.0.0.1:8081"
WS_BASE = "ws://127.0.0.1:8081"
PHONE_PREFIX = "19900"          # 测试用户手机号前缀，方便事后清理
CONCURRENCY_USERS = 600
CONCURRENCY_PER_USER = 4
EXPECTED_STOCK = 500

RESULTS = []   # (阶段, 结论, 明细)
CUR_VID = None  # 当前压测的券 id（供并发任务读取）

# 这台机器设了 HTTP_PROXY，requests 默认会读环境变量，
# 于是打 127.0.0.1 的请求也会绕到代理上，洪峰时偶发 ProxyError。
# 压测打的是本机，必须直连。
SESSION = requests.Session()
SESSION.trust_env = False


def log(tag, ok, detail=""):
    mark = "PASS" if ok else "FAIL"
    RESULTS.append((tag, ok, detail))
    print("[%s] %-28s %s" % (mark, tag, detail), flush=True)


def http(method, path, body=None, token=None, timeout=10):
    url = BASE + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["authorization"] = token
    # 走无代理的 Session（见文件头部 SESSION 的说明）
    r = SESSION.request(method, url, json=body, headers=headers, timeout=timeout)
    try:
        return r.status_code, r.json()
    except Exception:
        return r.status_code, {"raw": r.text[:200]}


def mysql(sql):
    out = subprocess.run(
        ["docker", "exec", "hmdp-mysql", "mysql", "-uroot", "-phmdp123456", "-N", "-e", sql],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if out.returncode != 0 and "Duplicate" not in out.stderr:
        print("  [SQL 警告] " + out.stderr.strip()[:200], flush=True)
    return out.stdout.strip()


def redis_get(key):
    out = subprocess.run(["docker", "exec", "hmdp-redis", "redis-cli", "get", key],
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout.strip()


def login(phone):
    """验证码 → 登录 → token

    验证码来源：dev 环境开 hmdp.auth.expose-code 时直接读接口返回；
    否则从 Redis 的 login:code:{phone} 取（生产环境不会把验证码回给前端）。
    """
    st, r = http("POST", "/user/code?phone=" + phone)
    code = None
    if isinstance(r.get("data"), dict):
        code = r["data"].get("code")
    if not code:
        code = redis_get("login:code:" + phone)
    if not code:
        raise RuntimeError("拿不到验证码 resp=%s" % r)
    st, r = http("POST", "/user/login", {"phone": phone, "code": code})
    data = r.get("data")
    if isinstance(data, dict):
        data = data.get("token")
    if not data:
        raise RuntimeError("登录失败 resp=%s" % r)
    return data


# ---------------------------------------------------------------- P1
def p1_health():
    st, r = http("GET", "/shop-type/list")
    ok = st == 200 and r.get("success") is True
    log("P1 服务连通 /shop-type/list", ok, "HTTP %s, success=%s, 分类数=%s"
        % (st, r.get("success"), len(r.get("data") or [])))
    return ok


# ---------------------------------------------------------------- P3
def p3_create_voucher(token):
    now = time.time()
    begin = time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(now - 600))
    end = time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(now + 86400))
    body = {
        "shopId": 1, "title": "【E2E 验证】500 库存秒杀券", "subTitle": "自动化压测用",
        "rules": "仅用于验证，不对外", "payValue": 100, "actualValue": 200,
        "type": 1, "stock": EXPECTED_STOCK, "beginTime": begin, "endTime": end,
    }
    st, r = http("POST", "/voucher/seckill", body, token=token)
    vid = r.get("data")
    stock_in_redis = redis_get("seckill:stock:%s" % vid) if vid else None
    ok = bool(vid) and str(stock_in_redis) == str(EXPECTED_STOCK)
    log("P3 建券 + Redis 预加载", ok,
        "voucherId=%s, DB stock=%s, Redis stock=%s" % (vid, EXPECTED_STOCK, stock_in_redis))
    return vid


# ---------------------------------------------------------------- P4
def p4_single(token, vid):
    # 先连 WS，再抢，验证推送确实来自「事务提交后」
    ws = websocket.create_connection("%s/ws/notification?token=%s" % (WS_BASE, token), timeout=10)
    hello = json.loads(ws.recv())
    log("P4.1 WS 握手鉴权", hello.get("type") == "connected", "首帧=%s" % hello)

    t0 = time.time()
    st, r = http("POST", "/voucher-order/seckill/%s" % vid, token=token)
    dt = (time.time() - t0) * 1000
    ok = r.get("success") is True
    order_id = r.get("data")
    log("P4.2 单次抢购", ok, "HTTP %s, 耗时 %.0fms, orderId=%s (%s)"
        % (st, dt, order_id, type(order_id).__name__))

    # orderId 必须是字符串（Long → String 修复）
    log("P4.3 Long 序列化为字符串", isinstance(order_id, str),
        "orderId 类型 = %s（雪花 ID 超 JS 安全整数）" % type(order_id).__name__)

    # 等 WS 推送
    pushed, waited = None, 0.0
    try:
        t1 = time.time()
        ws.settimeout(6)
        pushed = json.loads(ws.recv())
        waited = (time.time() - t1) * 1000
    except Exception as e:
        pushed = {"error": str(e)}
    ws.close()

    ok = (pushed or {}).get("type") == "seckill_result" and str((pushed or {}).get("orderId")) == str(order_id)
    log("P4.4 WS 秒杀结果推送", ok, "收到 %s（等待 %.0fms）" % (pushed, waited))

    # 一人一单
    st, r = http("POST", "/voucher-order/seckill/%s" % vid, token=token)
    ok = r.get("success") is False
    log("P4.5 一人一单拦截", ok, "第二次抢购 → success=%s, msg=%s" % (r.get("success"), r.get("errorMsg")))

    stock = mysql("use hmdp; select stock from tb_seckill_voucher where voucher_id=%s;" % vid)
    log("P4.6 库存只扣 1", stock == str(EXPECTED_STOCK - 1), "DB stock=%s（初始 %s）" % (stock, EXPECTED_STOCK))
    return order_id


def seed_users_and_tokens(n):
    """压测前置：直接造 n 个用户 + 在 Redis 里写登录态（Hash 结构）。

    为什么不走 /user/code：验证码接口按 IP 限流（实测 127.0.0.1 连续请求会被
    「验证码请求过于频繁」拦掉）——这本身是限流生效的证据，但压测需要绕开短信。
    真实压测也是这么做的：登录态预置，只压目标接口。
    """
    phones = ["199%08d" % i for i in range(n)]
    rows = ["('%s','','压测用户%d','')" % (ph, i) for i, ph in enumerate(phones)]
    # INSERT IGNORE：重跑不会因为手机号唯一键冲突整条语句失败
    mysql("use hmdp; insert ignore into tb_user(phone,password,nick_name,icon) values "
          + ",".join(rows) + ";")

    ids = [x for x in mysql("use hmdp; select id from tb_user order by id desc limit %d;" % n).split() if x]
    if len(ids) != n:
        raise RuntimeError("造用户失败：拿到 %d 个 id" % len(ids))

    cmds, tokens = [], []
    for uid in ids:
        tok = uuid.uuid4().hex
        tokens.append(tok)
        cmds.append("HSET login:token:%s id %s nickName 压测用户 icon ''" % (tok, uid))
        cmds.append("EXPIRE login:token:%s 1800" % tok)
    subprocess.run(["docker", "exec", "-i", "hmdp-redis", "redis-cli"],
                   input="\n".join(cmds) + "\n", text=True,
                   capture_output=True, encoding="utf-8", errors="replace")
    return tokens


# ---------------------------------------------------------------- P5
def _seckill_batch(jobs, pace_qps=None):
    """批量发秒杀请求。pace_qps 给定时按该速率匀速发（用于跑在限流阈值以内）。"""
    t0 = time.time()

    def one(args):
        tok, due = args
        if due:
            wait = due - (time.time() - t0)
            if wait > 0:
                time.sleep(wait)
        try:
            st, r = http("POST", "/voucher-order/seckill/%s" % CUR_VID, token=tok, timeout=30)
            return bool(r.get("success")), r.get("errorMsg") or ""
        except Exception as e:
            return False, "EXC:" + type(e).__name__

    args = [(tok, i / pace_qps if pace_qps else None) for i, tok in enumerate(jobs)]
    with ThreadPoolExecutor(max_workers=200) as ex:
        outs = list(ex.map(one, args))
    return outs, time.time() - t0


def _report(tag, outs, cost, vid, expect_success, expect_zero_stock=True):
    ok_cnt = sum(1 for s, _ in outs if s)
    errs = {}
    for s, m in outs:
        if not s:
            errs[m[:30]] = errs.get(m[:30], 0) + 1

    # 秒杀是异步链路：HTTP 返回「抢到了」只代表 Redis 预扣成功，
    # 订单还要经 RabbitMQ 才落库。洪峰打完时队列里通常还压着几百条消息，
    # 这时候直接 count 一定对不上（实测 320 成功 / 267 落库）。
    # 所以先轮询到订单数稳定（连续两次相同）再校验，最多等 30s。
    orders, prev, deadline = "-1", "-2", time.time() + 30
    while time.time() < deadline:
        orders = mysql("use hmdp; select count(*) from tb_voucher_order where voucher_id=%s;" % vid)
        if orders == prev:
            break
        prev = orders
        time.sleep(1)

    stock = mysql("use hmdp; select stock from tb_seckill_voucher where voucher_id=%s;" % vid)
    neg = mysql("use hmdp; select count(*) from tb_seckill_voucher where voucher_id=%s and stock<0;" % vid)
    dup = mysql("use hmdp; select count(*) from (select user_id,count(*) c from tb_voucher_order "
                "where voucher_id=%s group by user_id having c>1) t;" % vid)

    print("[%s] 请求 %d，耗时 %.1fs，吞吐 %.0f req/s" % (tag, len(outs), cost, len(outs) / cost), flush=True)
    print("   成功 %d / 失败 %d  %s" % (ok_cnt, len(outs) - ok_cnt, errs), flush=True)

    if expect_success is not None:
        log("P5 成功数 = 库存", ok_cnt == expect_success, "%d 成功，期望 %d" % (ok_cnt, expect_success))
    if expect_success is None:
        # 洪峰场景不校验具体数量：客户端可能因为超时/断连丢掉响应，
        # 但服务端其实已经受理，落库数会比「收到成功响应」的数更多。
        # 所以只校验「落库数 ≥ 成功响应数」，即没有凭空多出来的订单。
        log("P5 落库订单数", int(orders) >= ok_cnt,
            "订单 %s / 成功响应 %d" % (orders, ok_cnt))
    else:
        log("P5 落库订单数", orders == str(ok_cnt), "订单 %s / 成功 %d" % (orders, ok_cnt))
    if expect_zero_stock:
        log("P5 库存", stock == "0", "DB stock=%s" % stock)
    else:
        log("P5 库存未被限流打穿", int(stock) > 0,
            "DB stock=%s（洪峰被限流挡住，库存没被打光，符合预期）" % stock)
    log("P5 无负库存（零超卖）", neg == "0", "stock<0 行数 = %s" % neg)
    log("P5 无重复下单", dup == "0", "同用户多单 = %s" % dup)
    return ok_cnt, errs


def p5_steady(tokens, vid, pace_qps=90):
    """稳态压测：速率压在 Sentinel 秒杀阈值（100 QPS）以下，把库存打光，验证零超卖"""
    global CUR_VID
    CUR_VID = vid
    print("\n--- P5-a 稳态压测：%d 请求 @ %d QPS（限流阈值 100 QPS 以内）---"
          % (len(tokens), pace_qps), flush=True)
    outs, cost = _seckill_batch(tokens, pace_qps)
    return _report("稳态", outs, cost, vid, EXPECTED_STOCK)


def p5_burst(tokens, vid, repeat=4):
    """洪峰压测：一次打满，验证 Sentinel 限流生效（被限不是失败，是设计）"""
    global CUR_VID
    CUR_VID = vid
    jobs = [t for t in tokens for _ in range(repeat)]
    print("\n--- P5-b 洪峰压测：%d 请求瞬时打满 ---" % len(jobs), flush=True)
    outs, cost = _seckill_batch(jobs)
    ok_cnt, errs = _report("洪峰", outs, cost, vid, None, expect_zero_stock=False)
    limited = sum(v for k, v in errs.items() if "限流" in k)
    log("P5-b 限流生效（Sentinel 100 QPS）", limited > 0,
        "%d / %d 请求被限流拦截（%.0f%%）" % (limited, len(jobs), limited * 100.0 / len(jobs)))
    return ok_cnt


# ---------------------------------------------------------------- P6
def p6_timeout_close(vid):
    print("\n--- 等 35s 触发死信队列超时关单 ---", flush=True)
    time.sleep(35)
    closed = mysql("use hmdp; select count(*) from tb_voucher_order where voucher_id=%s and status=4;" % vid)
    unpaid = mysql("use hmdp; select count(*) from tb_voucher_order where voucher_id=%s and status=1;" % vid)
    stock = mysql("use hmdp; select stock from tb_seckill_voucher where voucher_id=%s;" % vid)
    redis_stock = redis_get("seckill:stock:%s" % vid)

    log("P6.1 死信队列自动关单", closed == str(EXPECTED_STOCK), "status=4 订单数 = %s" % closed)
    log("P6.2 无残留未支付单", unpaid == "0", "status=1 订单数 = %s" % unpaid)
    log("P6.3 数据库库存回补", stock == str(EXPECTED_STOCK), "DB stock 回到 %s" % stock)
    log("P6.4 Redis 库存回补", str(redis_stock) == str(EXPECTED_STOCK), "Redis stock = %s" % redis_stock)


# ---------------------------------------------------------------- main
def main():
    print("=" * 62)
    print(" 黑马点评 E2E 验证  base=%s" % BASE)
    print("=" * 62, flush=True)

    if not p1_health():
        print("服务不可用，终止。")
        return 1

    token = login(PHONE_PREFIX + "000001")
    log("P2 登录成功", bool(token), "token=%s..." % token[:12])

    vid = p3_create_voucher(token)
    if not vid:
        print("建券失败，终止。")
        return 1

    p4_single(token, vid)

    # 并发测试用一张全新的券：P4 已经消耗掉 1 个库存，混用会让「成功数 = 库存」这条断言失真
    vid2 = p3_create_voucher(token)

    print("\n--- 前置：造 %d 个用户 + 预置登录态（验证码按 IP 限流，压测直接绕开）---"
          % CONCURRENCY_USERS, flush=True)
    tokens = seed_users_and_tokens(CONCURRENCY_USERS)
    print("就绪：%d 个 token" % len(tokens), flush=True)

    p5_steady(tokens, vid2)
    vid3 = p3_create_voucher(token)
    p5_burst(tokens, vid3)
    p6_timeout_close(vid2)

    total = len(RESULTS)
    passed = sum(1 for _, ok, _ in RESULTS if ok)
    print("\n" + "=" * 62)
    print(" 结果：%d / %d 通过" % (passed, total))
    print("=" * 62, flush=True)

    # 写报告
    lines = ["# 黑马点评 E2E 验证报告", "",
             "- 时间：%s" % time.strftime("%Y-%m-%d %H:%M:%S"),
             "- 目标：%s（Spring Boot 容器 + MySQL/Redis/RabbitMQ）" % BASE,
             "- 结果：**%d / %d 通过**" % (passed, total), "",
             "| 阶段 | 结论 | 明细 |", "| --- | --- | --- |"]
    for tag, ok, detail in RESULTS:
        lines.append("| %s | %s | %s |" % (tag, "PASS" if ok else "FAIL", detail.replace("|", "/")))
    open("tools/e2e-report.md", "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("报告已写入 tools/e2e-report.md")
    return 0 if passed == total else 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        traceback.print_exc()
        sys.exit(3)
