package com.teachagain.app.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 模型接入设置（非敏感部分存普通 prefs；API Key 单独由 SecureKeyStore 加密保存）。
 * 支持 DeepSeek / OpenAI / 任意 OpenAI 兼容接口。
 */
public class AiSettings {

    public enum Provider {
        DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
        OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        COMPATIBLE("兼容接口", "", "");

        public final String label;
        public final String defaultBaseUrl;
        public final String defaultModel;

        Provider(String label, String baseUrl, String model) {
            this.label = label;
            this.defaultBaseUrl = baseUrl;
            this.defaultModel = model;
        }
    }

    private static final String PREFS = "ai_settings";
    private static final String K_PROVIDER = "provider";
    private static final String K_BASE_URL = "base_url";
    private static final String K_MODEL = "model";

    private final SharedPreferences prefs;
    private final SecureKeyStore keyStore;

    public AiSettings(Context ctx) {
        this.prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.keyStore = new SecureKeyStore(ctx);
    }

    public Provider getProvider() {
        String p = prefs.getString(K_PROVIDER, Provider.DEEPSEEK.name());
        try {
            return Provider.valueOf(p);
        } catch (Exception e) {
            return Provider.DEEPSEEK;
        }
    }

    public String getBaseUrl() {
        String v = prefs.getString(K_BASE_URL, null);
        if (v == null || v.isEmpty()) return getProvider().defaultBaseUrl;
        return v;
    }

    public String getModel() {
        String v = prefs.getString(K_MODEL, null);
        if (v == null || v.isEmpty()) return getProvider().defaultModel;
        return v;
    }

    /**
     * 保存非敏感设置。baseUrl/model 为空回落到 provider 默认。
     * 注意：Key 按 "provider+域名" 命名空间隔离存储；本方法不动 Key。
     * 切换供应商或域名后，旧命名空间的 Key 不会被读取、更不会发往新域名。
     */
    public void save(Provider provider, String baseUrl, String model) {
        prefs.edit()
                .putString(K_PROVIDER, provider.name())
                .putString(K_BASE_URL, baseUrl == null ? "" : baseUrl.trim())
                .putString(K_MODEL, model == null ? "" : model.trim())
                .apply();
    }

    /** 当前配置的 Key 命名空间 = 供应商名 + 目标域名 origin。 */
    public String keyNamespace() {
        return getProvider().name() + "@" + originOf(getBaseUrl());
    }

    /** 任意 provider+baseUrl 的命名空间（供设置页按界面当前选择判断/清除 Key）。 */
    public String namespaceFor(Provider provider, String baseUrl) {
        String url = (baseUrl == null || baseUrl.trim().isEmpty())
                ? provider.defaultBaseUrl : baseUrl;
        return provider.name() + "@" + originOf(url);
    }

    public boolean hasKeyFor(Provider provider, String baseUrl) {
        return keyStore.hasKey(namespaceFor(provider, baseUrl));
    }

    public void clearKeyFor(Provider provider, String baseUrl) {
        keyStore.clearKey(namespaceFor(provider, baseUrl));
    }

    public boolean saveKeyFor(Provider provider, String baseUrl, String plainKey) {
        return keyStore.saveKey(namespaceFor(provider, baseUrl), plainKey);
    }

    private static String originOf(String url) {
        if (url == null) return "";
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            String host = u.getHost();
            if (host == null) return url.trim();
            int port = u.getPort();
            return (u.getScheme() == null ? "https" : u.getScheme())
                    + "://" + host + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return url.trim();
        }
    }

    // ----- API Key（加密，按命名空间隔离）-----

    /** 当前供应商+域名下是否已存 Key。 */
    public boolean hasKey() {
        return keyStore.hasKey(keyNamespace());
    }

    public boolean saveKey(String plainKey) {
        return keyStore.saveKey(keyNamespace(), plainKey);
    }

    public void clearKey() {
        keyStore.clearKey(keyNamespace());
    }

    /** 仅在"当前配置的域名"下取回其专属 Key，杜绝跨域发送。 */
    String readKeyForRequest() {
        return keyStore.getKey(keyNamespace());
    }

    /** 是否具备真实联网调用的条件：有 base URL、model 和当前命名空间的 Key。 */
    public boolean isOnlineReady() {
        return hasKey()
                && getBaseUrl() != null && !getBaseUrl().isEmpty()
                && getModel() != null && !getModel().isEmpty();
    }
}
