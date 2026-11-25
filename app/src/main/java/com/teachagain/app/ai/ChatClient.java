package com.teachagain.app.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

/**
 * OpenAI 兼容的 /chat/completions 非流式客户端。
 *
 * 安全约束：
 * - 仅 HTTPS；拒绝明文 http。
 * - 关闭自动重定向（setInstanceFollowRedirects(false)），避免把凭据跟随跳转发到其他域名。
 * - 连接/读取超时；响应有大小上限，避免超大响应拖垮。
 * - 仅发送调用方给的 messages（由上层保证只含用户输入的应用/目标），不含屏幕或教程库。
 *
 * 当前：若未配置真实 Key 不会被调用（上层用本地模板）。此类为将来填 Key 后即可用。
 */
public class ChatClient {

    public static class ChatException extends Exception {
        public final int httpCode; // 0 表示非 HTTP 错误（网络/解析）
        public ChatException(int code, String msg) { super(msg); this.httpCode = code; }
    }

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 40000;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024; // 512KB 上限

    private volatile boolean cancelled = false;
    private volatile HttpURLConnection activeConn;

    public void cancel() {
        cancelled = true;
        HttpURLConnection c = activeConn;
        if (c != null) {
            try { c.disconnect(); } catch (Exception ignored) {}
        }
    }

    /**
     * 发送一次对话请求，返回 assistant 的文本内容。
     * @param baseUrl 形如 https://api.deepseek.com 或 .../v1
     * @param apiKey  Bearer Key
     * @param model   模型名
     * @param systemPrompt 系统提示
     * @param userContent 用户输入（应用+目标）
     */
    public String complete(String baseUrl, String apiKey, String model,
                           String systemPrompt, String userContent) throws ChatException {
        if (baseUrl == null || !baseUrl.trim().toLowerCase().startsWith("https://")) {
            throw new ChatException(0, "接口地址必须是 https:// 开头，暂不支持不安全连接。");
        }
        String endpoint = joinUrl(baseUrl.trim(), "chat/completions");

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            if (!(conn instanceof HttpsURLConnection)) {
                throw new ChatException(0, "接口必须使用 HTTPS。");
            }
            activeConn = conn;
            conn.setInstanceFollowRedirects(false); // 禁止跟随重定向，防凭据跨域
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("stream", false);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", userContent));
            body.put("messages", messages);
            body.put("temperature", 0.3);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            if (cancelled) throw new ChatException(0, "已取消。");

            int code = conn.getResponseCode();
            if (code == 401) throw new ChatException(401, "密钥无效或未授权（401）。请检查 API Key。");
            if (code == 429) throw new ChatException(429, "请求太频繁或额度用尽（429）。请稍后再试。");
            if (code == 302 || code == 301 || code == 307 || code == 308) {
                throw new ChatException(code, "接口发生了跳转，为安全起见已停止。请核对接口地址。");
            }
            if (code < 200 || code >= 300) {
                throw new ChatException(code, "服务返回错误（" + code + "）。");
            }

            return extractContent(readBounded(conn.getInputStream()));
        } catch (ChatException ce) {
            throw ce;
        } catch (java.net.SocketTimeoutException te) {
            throw new ChatException(0, "连接超时，请检查网络后重试。");
        } catch (Exception e) {
            if (cancelled) throw new ChatException(0, "已取消。");
            throw new ChatException(0, "网络出错了，请检查网络后重试。");
        } finally {
            activeConn = null;
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private String extractContent(String resp) throws ChatException {
        try {
            JSONObject o = new JSONObject(resp);
            JSONArray choices = o.getJSONArray("choices");
            if (choices.length() == 0) throw new ChatException(0, "服务没有返回内容。");
            JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
            return msg.getString("content");
        } catch (ChatException ce) {
            throw ce;
        } catch (Exception e) {
            throw new ChatException(0, "服务返回格式看不懂。");
        }
    }

    private String readBounded(InputStream in) throws ChatException {
        if (in == null) return "";
        try (InputStream stream = in) {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = stream.read(buf)) != -1) {
                if (cancelled) throw new ChatException(0, "已取消。");
                if (n > MAX_RESPONSE_BYTES - data.size()) {
                    throw new ChatException(0, "服务返回内容过大，已中止。");
                }
                data.write(buf, 0, n);
            }
            return data.toString("UTF-8");
        } catch (ChatException ce) {
            throw ce;
        } catch (Exception e) {
            throw new ChatException(0, "读取响应失败。");
        }
    }

    private String joinUrl(String base, String path) {
        if (base.endsWith("/")) return base + path;
        return base + "/" + path;
    }
}
