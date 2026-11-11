"""
临时 mock 后端 —— 只用于验证新前端页面的渲染效果。
监听 8081，nginx 的 /api 已经反代到这里。
数据按真实接口结构造，真实后端起来后直接停掉这个即可，前端无需改动。
"""

import json
import random
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

SHOPS = [
    (1, '103茶餐厅', 1, 'https://qcloud.dpfile.com/pc/jiclIsCKmOI2arxKN1Uf0Hx3PucIJH8q0QSz-Z8llzcN56-_QiKuOvyio1OOxsRtFoXqu0G3iT2T27qat3WhLVEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vfCF2ubeXzk49OsGrXt_KYDCngOyCwZK-s3fqawWswzk.jpg',
     '大关', '金华路锦昌文华苑29号', 80, 4215, 3035, 37, '10:00-22:00'),
    (2, '蔡馬洪涛烤肉·老北京铜锅涮羊肉', 1, 'https://p0.meituan.net/bbia/c1870d570e73accbc9fee90b48faca41195272.jpg',
     '拱宸桥/上塘', '上塘路1035号', 85, 2160, 1460, 46, '11:30-03:00'),
    (3, '新白鹿餐厅(运河上街店)', 1, 'https://p0.meituan.net/biztone/694233_1619500156517.jpeg',
     '运河上街', '台州路2号运河上街购物中心F5', 61, 12035, 8045, 47, '10:30-21:00'),
    (4, 'Mamala(杭州远洋乐堤港店)', 1, 'https://img.meituan.net/msmerchant/232f8fdf09050838bd33fb24e79f30f9606056.jpg',
     '拱宸桥/上塘', '丽水路66号远洋乐堤港', 290, 13519, 9529, 49, '11:00-22:00'),
    (5, '海底捞火锅(水晶城购物中心店)', 1, 'https://img.meituan.net/msmerchant/054b5de0ba0b50c18a620cc37482129a45739.jpg',
     '大关', '上塘路458号水晶城购物中心F6', 104, 4125, 2764, 49, '10:00-07:00'),
    (6, '幸福里老北京涮锅（丝联店）', 1, 'https://img.meituan.net/msmerchant/e71a2d0d693b3033c15522c43e03f09198239.jpg',
     '拱宸桥/上塘', '金华南路189号丝联166号', 130, 9531, 7324, 46, '11:00-13:50,17:00-20:50'),
    (7, '炉鱼(拱墅万达广场店)', 1, 'https://img.meituan.net/msmerchant/909434939a49b36f340523232924402166854.jpg',
     '北部新城', '杭行路666号万达商业中心', 85, 2631, 1320, 47, '00:00-24:00'),
    (8, '浅草屋寿司（运河上街店）', 1, 'https://img.meituan.net/msmerchant/cf3dff697bf7f6e11f4b79c4e7d989e4591290.jpg',
     '运河上街', '拱墅区金华路80号运河上街B1', 88, 2406, 1206, 46, '11:00-21:30'),
    (9, '羊老三羊蝎子北派炭火锅', 1, 'https://img.meituan.net/msmerchant/e478eb16f7e31a7f8b29b5e3bab6de205500837.jpg',
     '运河上街', '台州路2号运河上街F5', 101, 2763, 1363, 44, '11:00-21:30'),
    (10, '开乐迪KTV（运河上街店）', 2, 'https://p0.meituan.net/joymerchant/a575fd4adb0b9099c5c410058148b307-674435191.jpg',
     '运河上街', '台州路2号运河上街F4', 67, 26891, 902, 37, '00:00-24:00'),
    (11, 'INLOVE KTV(水晶城店)', 2, 'https://p0.meituan.net/dpmerchantpic/53e74b200211d68988a4f02ae9912c6c1076826.jpg',
     '水晶城', '上塘路458号水晶城6层', 75, 35977, 5684, 47, '11:30-06:00'),
    (12, '浅草美肌（运河上街店）', 3, '', '运河上街', '台州路2号运河上街B1', 168, 1420, 620, 43, '10:00-21:00'),
]

TYPES = [
    {'id': 1, 'name': '美食', 'icon': '/types/ms.png', 'sort': 1},
    {'id': 2, 'name': 'KTV', 'icon': '/types/KTV.png', 'sort': 2},
    {'id': 3, 'name': '丽人·美发', 'icon': '/types/lrmf.png', 'sort': 3},
    {'id': 4, 'name': '健身运动', 'icon': '/types/jsyd.png', 'sort': 4},
    {'id': 5, 'name': '按摩·足疗', 'icon': '/types/amzl.png', 'sort': 5},
    {'id': 6, 'name': '美容SPA', 'icon': '/types/spa.png', 'sort': 6},
    {'id': 7, 'name': '亲子游乐', 'icon': '/types/qzyl.png', 'sort': 7},
    {'id': 8, 'name': '酒吧', 'icon': '/types/jiuba.png', 'sort': 8},
]


def shop_json(s):
    return {
        'id': s[0], 'name': s[1], 'typeId': s[2], 'images': s[3],
        'area': s[4], 'address': s[5], 'avgPrice': s[6], 'sold': s[7],
        'comments': s[8], 'score': s[9], 'openHours': s[10],
        'x': 120.15, 'y': 30.32,
    }


BLOGS = [
    {'id': 1, 'title': '这家铜锅涮肉，两个人吃到扶墙', 'images': '', 'content':
        '手切鲜羊肉是招牌，涮八秒就够，蘸麻酱加一点韭菜花。\n两个人点了三盘肉一份糖蒜，人均 85，吃得非常实在。\n'
        '缺点是晚上七点后要等位，建议五点半就来。',
     'liked': 128, 'comments': 12, 'userId': 7, 'name': 'user_老饕', 'icon': '', 'createTime': '2026-11-06 19:20:00'},
    {'id': 2, 'title': '杭州 61 块吃到饱的杭帮菜', 'images': '', 'content':
        '新白鹿的性价比是真的顶。三个人点了东坡肉、龙井虾仁、干锅花菜，结账 183。',
     'liked': 96, 'comments': 8, 'userId': 8, 'name': 'user_小满', 'icon': '', 'createTime': '2026-11-05 12:10:00'},
    {'id': 3, 'title': '海底捞还是那个海底捞', 'images': '', 'content':
        '服务没得说，等位时候做了美甲。锅底推荐番茄加菌汤，麻辣的偏咸。',
     'liked': 54, 'comments': 3, 'userId': 9, 'name': 'user_阿丘', 'icon': '', 'createTime': '2026-11-04 20:02:00'},
]


class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'

    def log_message(self, *args):
        pass

    def _send(self, payload, code=200):
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def ok(self, data=None, total=None):
        payload = {'success': True, 'data': data}
        if total is not None:
            payload['total'] = total
        self._send(payload)

    def do_GET(self):
        u = urlparse(self.path)
        p = u.path
        q = parse_qs(u.query)

        if p == '/shop-type/list':
            return self.ok(TYPES)

        if p == '/shop/of/name':
            kw = (q.get('name', [''])[0] or '').strip()
            cur = int(q.get('current', ['1'])[0])
            pool = [s for s in SHOPS if kw in s[1]] if kw else SHOPS
            return self.ok([shop_json(s) for s in pool], len(pool))

        if p == '/shop/of/type':
            tid = int(q.get('typeId', ['1'])[0])
            cur = int(q.get('current', ['1'])[0])
            sort = q.get('sortBy', [''])[0]
            pool = [s for s in SHOPS if s[2] == tid]
            if sort == 'score':
                pool.sort(key=lambda s: -s[9])
            elif sort == 'comments':
                pool.sort(key=lambda s: -s[8])
            return self.ok([shop_json(s) for s in pool], len(pool))

        if p.startswith('/shop/'):
            sid = int(p.rsplit('/', 1)[-1])
            found = [s for s in SHOPS if s[0] == sid]
            return self.ok(shop_json(found[0]) if found else None)

        if p.startswith('/voucher/list/'):
            sid = int(p.rsplit('/', 1)[-1])
            return self.ok([
                {'id': 1, 'shopId': sid, 'title': '50元代金券', 'subTitle': '周一至周日均可使用',
                 'rules': '全场通用', 'payValue': 4750, 'actualValue': 5000, 'type': 0, 'status': 1},
                {'id': 2, 'shopId': sid, 'title': '100元秒杀券', 'subTitle': '限时抢，售完即止',
                 'rules': '不限时段', 'payValue': 6000, 'actualValue': 10000, 'type': 1, 'status': 1,
                 'stock': 37, 'beginTime': '2026-11-01 00:00:00', 'endTime': '2026-12-01 00:00:00'},
            ])

        if p == '/blog/hot':
            cur = int(q.get('current', ['1'])[0])
            return self.ok(BLOGS if cur == 1 else [], 3)

        # 注意：这些具体路径必须放在 /blog/{id} 之前，否则 "of" 会被当成数字 id
        if p == '/blog/of/me':
            return self.ok(BLOGS, 3)

        if p.startswith('/blog/likes/'):
            return self.ok([{'id': 8, 'nickName': 'user_小满', 'icon': ''}])

        if p == '/user/me':
            return self.ok({'id': 7, 'nickName': 'user_老饕', 'icon': ''})

        if p == '/user/sign/count':
            return self.ok(6)

        if p.startswith('/follow/or/not/'):
            return self.ok(False)

        if p.startswith('/blog/'):
            bid = int(p.rsplit('/', 1)[-1])
            found = [b for b in BLOGS if b['id'] == bid]
            if found:
                row = dict(found[0])
                row['isLike'] = False
                return self.ok(row)
            return self.ok(None)

        return self._send({'success': False, 'errorMsg': 'mock: 未实现 ' + p}, 200)

    def do_POST(self):
        u = urlparse(self.path)
        p = u.path
        length = int(self.headers.get('Content-Length') or 0)
        raw = self.rfile.read(length) if length else b''
        q = parse_qs(u.query)

        if p == '/user/code':
            form = parse_qs(raw.decode('utf-8'))
            phone = form.get('phone', [''])[0]
            return self.ok({'code': '123456'})

        if p == '/user/login':
            body = json.loads(raw.decode('utf-8') or '{}')
            return self.ok('mock-token-' + str(int(time.time())))

        if p.startswith('/voucher-order/seckill/'):
            return self.ok(str(1900000000000000000 + random.randint(1, 9999)))

        if p == '/user/logout':
            return self.ok()

        return self._send({'success': False, 'errorMsg': 'mock: 未实现 ' + p}, 200)

    def do_PUT(self):
        return self.ok()


if __name__ == '__main__':
    print('mock api on 8081')
    ThreadingHTTPServer(('127.0.0.1', 8081), Handler).serve_forever()
