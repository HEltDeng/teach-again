package com.teachagain.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 用 Android Keystore 的 AES-GCM 在本机加密保存 API Key。
 *
 * 安全约束：
 * - 密钥材料由系统 Keystore 托管，应用拿不到原始密钥。
 * - 只存密文 + IV（Base64），明文 Key 不落盘、不打日志、不导出、不写进源码。
 * - 读取时在内存解密，用完即弃。
 */
public class SecureKeyStore {

    private static final String KS_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "teachagain_api_key";
    private static final String PREFS = "ai_secure";
    private static final String PREF_CIPHER_BASE = "key_cipher";
    private static final String PREF_IV_BASE = "key_iv";
    private static final String TRANSFORMATION =
            KeyProperties.KEY_ALGORITHM_AES + "/"
                    + KeyProperties.BLOCK_MODE_GCM + "/"
                    + KeyProperties.ENCRYPTION_PADDING_NONE;
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences prefs;

    public SecureKeyStore(Context ctx) {
        this.prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // 按命名空间（供应商+域名）隔离存储，避免把某供应商的 Key 发往其他域名
    private String cipherKey(String ns) { return PREF_CIPHER_BASE + "_" + ns; }
    private String ivKey(String ns) { return PREF_IV_BASE + "_" + ns; }

    public boolean hasKey(String ns) {
        return prefs.contains(cipherKey(ns)) && prefs.contains(ivKey(ns));
    }

    /** 保存（加密）Key 到指定命名空间。空串等同清除。 */
    public boolean saveKey(String ns, String plainKey) {
        if (plainKey == null || plainKey.isEmpty()) {
            clearKey(ns);
            return true;
        }
        try {
            SecretKey secretKey = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] ct = cipher.doFinal(plainKey.getBytes("UTF-8"));
            prefs.edit()
                    .putString(cipherKey(ns), Base64.encodeToString(ct, Base64.NO_WRAP))
                    .putString(ivKey(ns), Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 解密取回指定命名空间的 Key；无或失败返回 null。用完不要缓存。 */
    public String getKey(String ns) {
        if (!hasKey(ns)) return null;
        try {
            SecretKey secretKey = getOrCreateKey();
            String ctB64 = prefs.getString(cipherKey(ns), null);
            String ivB64 = prefs.getString(ivKey(ns), null);
            if (ctB64 == null || ivB64 == null) return null;
            byte[] ct = Base64.decode(ctB64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey,
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public void clearKey(String ns) {
        prefs.edit().remove(cipherKey(ns)).remove(ivKey(ns)).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS_PROVIDER);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry =
                    (KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KS_PROVIDER);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        kg.init(spec);
        return kg.generateKey();
    }
}
