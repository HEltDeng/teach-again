package com.teachagain.app.ai;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.teachagain.app.InsetsUtil;
import com.teachagain.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型设置页：选供应商、填 base URL / model / API Key（密码式，可留空）。
 * Key 按"供应商+域名"命名空间加密保存，切换供应商/域名不会把旧 Key 发往新域名。
 */
public class AiSettingsActivity extends AppCompatActivity {

    private Spinner spinnerProvider;
    private EditText etBaseUrl, etModel, etApiKey;
    private TextView tvKeyStatus;

    private AiSettings settings;
    private AiSettings.Provider[] providers;
    private boolean suppressSpinner = false;
    private int lastProviderPos = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_ai_settings);
        InsetsUtil.applySystemBars(findViewById(R.id.root));

        settings = new AiSettings(this);
        spinnerProvider = findViewById(R.id.spinner_provider);
        etBaseUrl = findViewById(R.id.et_base_url);
        etModel = findViewById(R.id.et_model);
        etApiKey = findViewById(R.id.et_api_key);
        tvKeyStatus = findViewById(R.id.tv_key_status);

        providers = AiSettings.Provider.values();
        List<String> labels = new ArrayList<>();
        for (AiSettings.Provider p : providers) labels.add(p.label);
        spinnerProvider.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));

        // 初始化为已保存值（先填文本，再设选项，避免首次异步回调覆盖已存 URL/model）
        AiSettings.Provider cur = settings.getProvider();
        etBaseUrl.setText(settings.getBaseUrl());
        etModel.setText(settings.getModel());
        lastProviderPos = indexOf(cur);
        suppressSpinner = true;
        spinnerProvider.setSelection(lastProviderPos);
        suppressSpinner = false;

        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (suppressSpinner) return;
                if (pos == lastProviderPos) return; // provider 未真正改变，不动用户已填内容
                lastProviderPos = pos;
                AiSettings.Provider p = providers[pos];
                // 供应商真正切换：用其默认 base URL / model 填充（兼容接口留空让用户填）
                etBaseUrl.setText(p.defaultBaseUrl);
                etModel.setText(p.defaultModel);
                // 清空 Key 输入框：旧供应商 Key 不会被带到新域名
                etApiKey.setText("");
                refreshKeyStatus();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> onSave());
        findViewById(R.id.btn_clear_key).setOnClickListener(v -> onClearKey());

        refreshKeyStatus();
    }

    private int indexOf(AiSettings.Provider p) {
        for (int i = 0; i < providers.length; i++) if (providers[i] == p) return i;
        return 0;
    }

    private AiSettings.Provider currentProvider() {
        int pos = spinnerProvider.getSelectedItemPosition();
        if (pos < 0 || pos >= providers.length) return providers[0];
        return providers[pos];
    }

    private void refreshKeyStatus() {
        // 以界面当前选中的 provider + URL 为准判断 Key 是否已存
        boolean has = settings.hasKeyFor(currentProvider(),
                etBaseUrl.getText().toString().trim());
        if (has) {
            tvKeyStatus.setText("这个供应商+域名下已保存 Key（加密）。留空保存不改动它；要更换请重填。");
        } else {
            tvKeyStatus.setText("这个供应商+域名下未保存 Key。不填则使用本地体验。");
        }
    }

    private void onSave() {
        AiSettings.Provider p = providers[spinnerProvider.getSelectedItemPosition()];
        String baseUrl = etBaseUrl.getText().toString().trim();
        String model = etModel.getText().toString().trim();

        if (p == AiSettings.Provider.COMPATIBLE && baseUrl.isEmpty()) {
            Toast.makeText(this, "兼容接口需要填写 Base URL", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!baseUrl.isEmpty() && !baseUrl.toLowerCase().startsWith("https://")) {
            Toast.makeText(this, "接口地址必须以 https:// 开头", Toast.LENGTH_LONG).show();
            return;
        }

        // 先把 Key 写入"这次界面选择"对应的命名空间（非空才写），再保存非敏感设置
        String key = etApiKey.getText().toString();
        if (key != null && !key.isEmpty()) {
            boolean ok = settings.saveKeyFor(p, baseUrl, key);
            etApiKey.setText(""); // 不在界面保留明文
            if (!ok) {
                Toast.makeText(this, "Key 加密保存失败，请重试", Toast.LENGTH_LONG).show();
                return;
            }
        }
        settings.save(p, baseUrl, model);

        Toast.makeText(this,
                settings.isOnlineReady() ? "已保存，现在会用模型生成" : "已保存，当前使用本地体验",
                Toast.LENGTH_SHORT).show();
        refreshKeyStatus();
        finish();
    }

    private void onClearKey() {
        // 只清除界面当前选中的 provider+URL 下的 Key，不误删其他域名
        settings.clearKeyFor(currentProvider(), etBaseUrl.getText().toString().trim());
        etApiKey.setText("");
        Toast.makeText(this, "已清除这个供应商+域名下的 Key", Toast.LENGTH_SHORT).show();
        refreshKeyStatus();
    }
}
