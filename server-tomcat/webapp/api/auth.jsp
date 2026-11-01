<%@ page contentType="application/json; charset=UTF-8" pageEncoding="UTF-8" session="false"
         trimDirectiveWhitespaces="true"
         import="java.io.File,java.nio.charset.StandardCharsets,java.nio.file.Files,java.nio.file.StandardOpenOption,java.security.MessageDigest,java.security.SecureRandom,java.util.LinkedHashMap,java.util.Map,java.util.regex.Pattern,jakarta.servlet.ServletContext" %>
<%--
  ============ server-tomcat/webapp/api/auth.jsp（登录 / 注册接口）============
  访问地址：POST http://localhost:8080/campus-canteen/api/auth.jsp
  参数：action=login|register & username=... & password=...
  返回：{"code":0,"message":"登录成功","username":"xxx"}
        code 0 成功 / 1 用户名已存在 / 2 账号或密码错误 / 3 参数不合法 / 4 服务器异常
  账号：保存在本应用 WEB-INF/users.tsv，格式 用户名 \t 盐 \t 盐+SHA256哈希
        （WEB-INF 目录浏览器访问不到，密码不存明文）
--%>
<%!
    private static final Pattern USERNAME_RE = Pattern.compile("[A-Za-z0-9_]{3,20}");
    private static final Pattern PASSWORD_RE = Pattern.compile("[\\x21-\\x7E]{6,20}");

    /** 加盐 SHA-256：同样的密码，盐不同哈希就不同 */
    private static String hashPassword(String salt, String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String newSalt() {
        byte[] buf = new byte[8];
        new SecureRandom().nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /** 手写一个最小 JSON 字符串转义（只用于拼返回体） */
    private static String json(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** 账号文件位置：正常情况在 WEB-INF 下；如果应用被打成 war 包，退回系统临时目录 */
    private static File usersFile(ServletContext ctx) {
        String real = ctx.getRealPath("/WEB-INF/users.tsv");
        if (real != null) {
            return new File(real);
        }
        return new File(System.getProperty("java.io.tmpdir"), "campus-canteen-users.tsv");
    }

    /** 读账号表：小写用户名 → {原用户名, 盐, 哈希}（用户名不区分大小写） */
    private static Map<String, String[]> readUsers(File file) throws Exception {
        Map<String, String[]> map = new LinkedHashMap<>();
        if (!file.isFile()) {
            return map;
        }
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] p = line.split("\t");
            if (p.length < 3) {
                continue;
            }
            map.put(p[0].toLowerCase(), new String[]{p[0], p[1], p[2]});
        }
        return map;
    }
%>
<%
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    response.setHeader("Cache-Control", "no-store");
    request.setCharacterEncoding("UTF-8");

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
        response.setStatus(204);
        return;
    }
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        out.print("{\"code\":3,\"message\":\"请使用 POST 调用登录注册接口\"}");
        return;
    }

    String action = request.getParameter("action");
    String username = request.getParameter("username");
    String password = request.getParameter("password");
    username = username == null ? "" : username.trim();
    password = password == null ? "" : password;

    if (action == null || !("login".equals(action) || "register".equals(action))) {
        out.print("{\"code\":3,\"message\":\"action 只能是 login 或 register\"}");
        return;
    }
    if (!USERNAME_RE.matcher(username).matches()) {
        out.print("{\"code\":3,\"message\":\"用户名需为 3-20 位字母、数字或下划线\"}");
        return;
    }
    if (!PASSWORD_RE.matcher(password).matches()) {
        out.print("{\"code\":3,\"message\":\"密码需为 6-20 位字符（不含空格）\"}");
        return;
    }

    // 账号文件读写串行化：避免两个请求同时注册同一个用户名
    synchronized (application) {
        try {
            File file = usersFile(application);
            Map<String, String[]> users = readUsers(file);
            String key = username.toLowerCase();

            if ("register".equals(action)) {
                if (users.containsKey(key)) {
                    out.print("{\"code\":1,\"message\":\"用户名已被注册\"}");
                    return;
                }
                String salt = newSalt();
                String hash = hashPassword(salt, password);
                Files.write(file.toPath(),
                        (username + "\t" + salt + "\t" + hash + System.lineSeparator())
                                .getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                log("[注册] " + username);
                out.print("{\"code\":0,\"message\":\"注册成功\",\"username\":\"" + json(username) + "\"}");
                return;
            }

            String[] user = users.get(key);
            if (user == null || !user[2].equals(hashPassword(user[1], password))) {
                out.print("{\"code\":2,\"message\":\"用户名或密码错误\"}");
                return;
            }
            log("[登录] " + user[0]);
            out.print("{\"code\":0,\"message\":\"登录成功\",\"username\":\"" + json(user[0]) + "\"}");
        } catch (Exception e) {
            log("[500] auth.jsp " + e);
            out.print("{\"code\":4,\"message\":\"服务器处理失败：" + json(e.getMessage()) + "\"}");
        }
    }
%>
