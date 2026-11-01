package com.teachagain.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * "家人教一次"：选任务 + 称呼 → 本地模板生成分步草稿 → 可编辑 → 保存本机 / 分享。
 * 明确称为"生成步骤"，不宣称在线 AI。
 */
public class FamilyCreateActivity extends AppCompatActivity {

    /** 传入已有自定义教程 id 则进入编辑模式，保存时覆盖原教程。 */
    public static final String EXTRA_EDIT_ID = "edit_id";
    /** 传入一份草稿教程（JSON），进入可编辑预览（用于 AI/本地生成结果）。 */
    public static final String EXTRA_DRAFT_JSON = "draft_json";
    /** 草稿来源标签（如"本地体验"/"模型生成"），仅用于界面如实标注。 */
    public static final String EXTRA_DRAFT_SOURCE = "draft_source";

    private Spinner spinnerTask;
    private EditText etAddressee;
    private EditText etTitle;
    private LinearLayout stepsContainer;
    private Button btnAddStep, btnSave, btnShare;

    private List<StepTemplates.TaskOption> options;
    private String editingId = null; // 非空=编辑模式，保存沿用此 id
    private boolean draftMode = false; // 草稿预览模式（来自 AI/本地生成）
    private TextView tvSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_family_create);
        InsetsUtil.applySystemBars(findViewById(R.id.root));

        spinnerTask = findViewById(R.id.spinner_task);
        etAddressee = findViewById(R.id.et_addressee);
        etTitle = findViewById(R.id.et_title);
        stepsContainer = findViewById(R.id.steps_container);
        btnAddStep = findViewById(R.id.btn_add_step);
        btnSave = findViewById(R.id.btn_save);
        btnShare = findViewById(R.id.btn_share);
        tvSource = findViewById(R.id.tv_source);

        options = StepTemplates.taskOptions();
        List<String> labels = new ArrayList<>();
        for (StepTemplates.TaskOption o : options) labels.add(o.label);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        spinnerTask.setAdapter(adapter);

        findViewById(R.id.btn_generate).setOnClickListener(v -> onGenerate());
        btnAddStep.setOnClickListener(v -> addStepRow("", ""));
        btnSave.setOnClickListener(v -> onSave());
        btnShare.setOnClickListener(v -> onShare());

        // 草稿预览模式（AI/本地生成）：载入一份可编辑草稿
        String draftJson = getIntent().getStringExtra(EXTRA_DRAFT_JSON);
        if (draftJson != null) {
            try {
                Tutorial draft = Tutorial.fromJson(new org.json.JSONObject(draftJson));
                String src = getIntent().getStringExtra(EXTRA_DRAFT_SOURCE);
                loadDraft(draft, src);
            } catch (Exception ignored) {}
            return;
        }

        // 编辑模式：载入已有教程
        String editId = getIntent().getStringExtra(EXTRA_EDIT_ID);
        if (editId != null) {
            Tutorial existing = new TutorialRepository(this).getById(editId);
            if (existing != null && !existing.builtin) {
                loadForEdit(existing);
            }
        }
    }

    /** 载入生成草稿：新建（不沿用 id），隐藏生成控件，页内如实标注来源。 */
    private void loadDraft(Tutorial draft, String source) {
        editingId = null;   // 草稿是新教程，保存时生成新 id
        draftMode = true;

        // 隐藏旧的任务下拉/称呼/生成模板控件，避免覆盖新草稿
        View genControls = findViewById(R.id.gen_controls);
        if (genControls != null) genControls.setVisibility(View.GONE);

        // 页内来源标注（NoActionBar 下 setTitle 无效、Toast 会消失）
        if (tvSource != null) {
            tvSource.setVisibility(View.VISIBLE);
            tvSource.setText("来源：" + (source == null ? "生成步骤" : source)
                    + "。可逐条修改，确认无误再保存。");
        }

        etTitle.setText(draft.title);
        stepsContainer.removeAllViews();
        for (Tutorial.Step s : draft.steps) addStepRow(s.title, s.desc);
        btnAddStep.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
        btnSave.setText("保存并跟学"); // 草稿模式：保存后直接进入跟学
        btnShare.setVisibility(View.VISIBLE);
    }

    private void loadForEdit(Tutorial t) {
        editingId = t.id;
        setTitle("修改教程");
        etTitle.setText(t.title);
        stepsContainer.removeAllViews();
        for (Tutorial.Step s : t.steps) addStepRow(s.title, s.desc);
        btnAddStep.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.VISIBLE);
    }

    private void onGenerate() {
        int pos = spinnerTask.getSelectedItemPosition();
        if (pos < 0 || pos >= options.size()) return;
        StepTemplates.TaskOption opt = options.get(pos);
        String addressee = etAddressee.getText().toString();

        // 自动填教程名（用户可改）
        if (TextUtils.isEmpty(etTitle.getText())) {
            String t = opt.label.replace("教", "").trim();
            etTitle.setText(t);
        }

        List<Tutorial.Step> steps = StepTemplates.generate(opt.key, addressee);
        stepsContainer.removeAllViews();
        for (Tutorial.Step s : steps) addStepRow(s.title, s.desc);

        btnAddStep.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
        btnShare.setVisibility(View.VISIBLE);
    }

    /** 每一步一行：标题输入 + 说明输入 + 删除按钮。 */
    private void addStepRow(String title, String desc) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundResource(R.drawable.card_soft);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(10);
        row.setLayoutParams(rowLp);

        TextView num = new TextView(this);
        num.setTextColor(getColor(R.color.primary));
        num.setTextSize(14);
        row.addView(num);

        EditText etStepTitle = new EditText(this);
        etStepTitle.setHint("这一步做什么（最多30字）");
        etStepTitle.setText(title);
        etStepTitle.setTextSize(17);
        etStepTitle.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(TutorialImporter.MAX_STEP_TITLE)});
        etStepTitle.setTag("title");
        row.addView(etStepTitle);

        EditText etDesc = new EditText(this);
        etDesc.setHint("详细说明：在哪个App、点哪里、看到什么（最多200字）");
        etDesc.setText(desc);
        etDesc.setTextSize(15);
        etDesc.setMinLines(2);
        etDesc.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(TutorialImporter.MAX_STEP_DESC)});
        etDesc.setTag("desc");
        row.addView(etDesc);

        Button del = new Button(this);
        del.setText("删除这一步");
        del.setAllCaps(false);
        del.setTextSize(14);
        del.setBackgroundResource(R.drawable.btn_secondary);
        del.setOnClickListener(v -> {
            stepsContainer.removeView(row);
            renumber();
        });
        row.addView(del);

        stepsContainer.addView(row);
        renumber();
    }

    private void renumber() {
        for (int i = 0; i < stepsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) stepsContainer.getChildAt(i);
            TextView num = (TextView) row.getChildAt(0);
            num.setText("第 " + (i + 1) + " 步");
        }
    }

    private void onSave() {
        Tutorial t = collect();
        if (t == null) return;

        // 草稿模式：若仍有"待家人补全"占位，阻止直接跟学，提示先补具体步骤
        if (draftMode && hasPlaceholder(t)) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("还差一点")
                    .setMessage("有步骤还是「待家人补全」的占位内容。请先改成具体怎么操作、"
                            + "点完看到什么，再保存给长辈跟学。")
                    .setPositiveButton("我去补全", null)
                    .show();
            return;
        }

        TutorialRepository repo = new TutorialRepository(this);
        if (!repo.saveUserTutorial(t)) {
            Toast.makeText(this, "保存失败，存储空间可能不足，请重试",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (draftMode) {
            // 保存并跟学：直接打开详情页（新 id），再关闭编辑页
            Toast.makeText(this, "已保存：" + t.title, Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, TutorialDetailActivity.class);
            i.putExtra(TutorialDetailActivity.EXTRA_TUTORIAL_ID, t.id);
            startActivity(i);
            finish();
        } else {
            Toast.makeText(this, "已保存到本机：" + t.title, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** 是否仍含"待家人补全"占位步骤。 */
    private boolean hasPlaceholder(Tutorial t) {
        for (Tutorial.Step s : t.steps) {
            if (s.title != null && s.title.contains("待家人补全")) return true;
            if (s.desc != null && s.desc.contains("待家人补全")) return true;
        }
        return false;
    }

    private void onShare() {
        Tutorial t = collect();
        if (t == null) return;
        String text = TutorialImporter.export(t);
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(android.content.Intent.EXTRA_TEXT, text);
        try {
            startActivity(android.content.Intent.createChooser(send, "分享教程"));
        } catch (Exception e) {
            Toast.makeText(this, "没有可用的分享应用", Toast.LENGTH_SHORT).show();
        }
    }

    /** 收集并校验表单，复用 Importer 的规则。 */
    private Tutorial collect() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "请填写教程名称", Toast.LENGTH_SHORT).show();
            return null;
        }
        if (title.length() > TutorialImporter.MAX_TITLE_LEN) {
            Toast.makeText(this, "教程名称太长了", Toast.LENGTH_SHORT).show();
            return null;
        }

        List<Tutorial.Step> steps = new ArrayList<>();
        for (int i = 0; i < stepsContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) stepsContainer.getChildAt(i);
            EditText etT = row.findViewWithTag("title");
            EditText etD = row.findViewWithTag("desc");
            String st = etT.getText().toString().trim();
            String sd = etD.getText().toString().trim();
            if (st.isEmpty()) {
                Toast.makeText(this, "第 " + (i + 1) + " 步还没写标题",
                        Toast.LENGTH_SHORT).show();
                return null;
            }
            if (sd.isEmpty()) sd = st;
            steps.add(new Tutorial.Step(st, sd));
        }

        if (steps.size() < TutorialImporter.MIN_STEPS) {
            Toast.makeText(this, "至少要 " + TutorialImporter.MIN_STEPS + " 步",
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        if (steps.size() > TutorialImporter.MAX_STEPS) {
            Toast.makeText(this, "步骤太多了，最多 " + TutorialImporter.MAX_STEPS + " 步",
                    Toast.LENGTH_SHORT).show();
            return null;
        }

        // 编辑模式沿用原 id（覆盖保存），否则新建
        String id = editingId != null ? editingId : Tutorial.newUserId();
        return new Tutorial(id, title, steps, false);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
