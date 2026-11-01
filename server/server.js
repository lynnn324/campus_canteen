/*
 * ============ 校园食堂 · 数据服务器 ============
 * 作用：
 *   1) 给 App 提供档口（shops）和菜品（dishes）两份 JSON 数据接口；
 *   2) 提供登录 / 注册接口，账号保存在 data/users.tsv（加盐 SHA-256，不存明文密码）。
 * 特点：零依赖，只用 Node 自带模块，不需要 npm install。
 * 启动：在本目录执行  node server.js
 * 接口：
 *   GET  /api/shops   档口列表
 *   GET  /api/dishes  菜品列表
 *   GET  /api/version 数据版本
 *   POST /api/auth    action=login|register，username=...，password=...
 */

const http = require('http');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8080);
const HOST = '0.0.0.0';
const DATA_DIR = path.join(__dirname, 'data');
const VERSION = '1.1.0';
const USERS_FILE = path.join(DATA_DIR, 'users.tsv');

// 业务返回码：跟 Tomcat 那边的 auth.jsp 保持一致，App 只认这几个数
const CODE_OK = 0;        // 成功
const CODE_EXISTS = 1;    // 注册时用户名已存在
const CODE_AUTH = 2;      // 登录时用户名或密码错误
const CODE_PARAM = 3;     // 参数不合法
const CODE_SERVER = 4;    // 服务器读写失败

const USERNAME_RE = /^[A-Za-z0-9_]{3,20}$/;
const PASSWORD_RE = /^[\x21-\x7E]{6,20}$/;

// 文件 → 接口路径 的映射：每次请求都重新读文件，改完 json 不用重启服务
const ROUTES = {
    '/api/shops': { file: 'shops.json', key: 'shops' },
    '/api/dishes': { file: 'dishes.json', key: 'dishes' }
};

function sendJson(res, status, obj) {
    const body = JSON.stringify(obj);
    res.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(body),
        // 允许 App / 浏览器跨域读取，方便课堂用浏览器直接看接口返回
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS'
    });
    res.end(body);
}

function readData(route) {
    const filePath = path.join(DATA_DIR, route.file);
    const text = fs.readFileSync(filePath, 'utf8');
    const json = JSON.parse(text);
    if (!Array.isArray(json[route.key])) {
        throw new Error(route.file + ' 缺少 ' + route.key + ' 数组');
    }
    return json[route.key];
}

// ---------- 账号 ----------

function hashPassword(salt, password) {
    return crypto.createHash('sha256').update(salt + ':' + password, 'utf8').digest('hex');
}

/** users.tsv 每行：用户名 \t 盐 \t 哈希；返回小写用户名 → 记录 的映射 */
function readUsers() {
    const map = {};
    if (!fs.existsSync(USERS_FILE)) {
        return map;
    }
    fs.readFileSync(USERS_FILE, 'utf8').split(/\r?\n/).forEach(line => {
        if (!line.trim()) return;
        const parts = line.split('\t');
        if (parts.length < 3) return;
        map[parts[0].toLowerCase()] = { username: parts[0], salt: parts[1], hash: parts[2] };
    });
    return map;
}

function appendUser(username, salt, hash) {
    if (!fs.existsSync(DATA_DIR)) {
        fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.appendFileSync(USERS_FILE, [username, salt, hash].join('\t') + '\n', 'utf8');
}

/** 读请求体，超过 10KB 直接丢弃（防止有人传大文件把内存撑爆） */
function readBody(req, callback) {
    let body = '';
    let tooLarge = false;
    req.on('data', chunk => {
        if (tooLarge) return;
        body += chunk;
        if (body.length > 10240) {
            tooLarge = true;
        }
    });
    req.on('end', () => callback(tooLarge ? null : body));
}

function handleAuth(req, res) {
    readBody(req, body => {
        if (body === null) {
            return sendJson(res, 200, { code: CODE_PARAM, message: '请求内容过大' });
        }
        const params = new URLSearchParams(body);
        const action = params.get('action') || '';
        const username = (params.get('username') || '').trim();
        const password = params.get('password') || '';

        if (!USERNAME_RE.test(username)) {
            return sendJson(res, 200, {
                code: CODE_PARAM,
                message: '用户名需为 3-20 位字母、数字或下划线'
            });
        }
        if (!PASSWORD_RE.test(password)) {
            return sendJson(res, 200, {
                code: CODE_PARAM,
                message: '密码需为 6-20 位字符（不含空格）'
            });
        }

        try {
            const users = readUsers();
            const key = username.toLowerCase();

            if (action === 'register') {
                if (users[key]) {
                    return sendJson(res, 200, { code: CODE_EXISTS, message: '用户名已被注册' });
                }
                const salt = crypto.randomBytes(8).toString('hex');
                appendUser(username, salt, hashPassword(salt, password));
                console.log('[注册] ' + username);
                return sendJson(res, 200, {
                    code: CODE_OK,
                    message: '注册成功',
                    username
                });
            }

            if (action === 'login') {
                const user = users[key];
                if (!user || user.hash !== hashPassword(user.salt, password)) {
                    return sendJson(res, 200, { code: CODE_AUTH, message: '用户名或密码错误' });
                }
                console.log('[登录] ' + user.username);
                return sendJson(res, 200, {
                    code: CODE_OK,
                    message: '登录成功',
                    username: user.username
                });
            }

            return sendJson(res, 200, { code: CODE_PARAM, message: 'action 只能是 login 或 register' });
        } catch (e) {
            console.error('[500] /api/auth  ' + e.message);
            return sendJson(res, 200, { code: CODE_SERVER, message: '服务器处理失败：' + e.message });
        }
    });
}

const server = http.createServer((req, res) => {
    // 浏览器预检请求
    if (req.method === 'OPTIONS') {
        res.writeHead(204, {
            'Access-Control-Allow-Origin': '*',
            'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
            'Access-Control-Allow-Headers': 'Content-Type'
        });
        return res.end();
    }

    const url = req.url.split('?')[0];

    // 登录 / 注册：唯一支持 POST 的接口
    if (req.method === 'POST' && url === '/api/auth') {
        return handleAuth(req, res);
    }

    if (req.method !== 'GET') {
        return sendJson(res, 405, { code: 405, message: '只支持 GET 请求（登录注册用 POST /api/auth）' });
    }

    // 版本接口：App 设置页可用来显示"数据版本"
    if (url === '/api/version') {
        return sendJson(res, 200, { version: VERSION, time: new Date().toISOString() });
    }

    const route = ROUTES[url];
    if (!route) {
        return sendJson(res, 404, {
            code: 404,
            message: '接口不存在，可用：/api/shops、/api/dishes、/api/version、POST /api/auth'
        });
    }

    try {
        const data = readData(route);
        sendJson(res, 200, {
            version: VERSION,
            count: data.length,
            [route.key]: data
        });
        console.log('[200] ' + url + '  ' + data.length + ' 条');
    } catch (e) {
        console.error('[500] ' + url + '  ' + e.message);
        sendJson(res, 500, { code: 500, message: '服务器读取数据失败：' + e.message });
    }
});

server.listen(PORT, HOST, () => {
    console.log('校园食堂数据服务器已启动：http://localhost:' + PORT);
    console.log('  GET  /api/shops');
    console.log('  GET  /api/dishes');
    console.log('  GET  /api/version');
    console.log('  POST /api/auth   （action=login|register）');
    console.log('');
    const nets = os.networkInterfaces();
    Object.keys(nets).forEach(name => {
        (nets[name] || []).forEach(net => {
            if (net.family === 'IPv4' && !net.internal) {
                console.log('局域网地址（真机调试用）：http://' + net.address + ':' + PORT);
            }
        });
    });
    console.log('');
    console.log('USB 真机/模拟器可执行：adb reverse tcp:' + PORT + ' tcp:' + PORT);
    console.log('这样 App 访问 http://127.0.0.1:' + PORT + ' 就会转发到本机服务器。');
});
