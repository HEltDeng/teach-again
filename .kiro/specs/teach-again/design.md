# 设计文档 — 再教我一次

## 架构总览
```
MainActivity
  └─ 权限引导 → 跳 Settings
  └─ 首页 (TutorialListFragment)
       └─ TutorialDetailActivity (选教程/称呼)
FloatingBallService (前台服务)
  └─ FloatingBallView (球 WindowManager)
  └─ StepCardView (卡片 WindowManager)
  └─ TtsHelper (TextToSpeech)
  └─ ProgressRepository (SharedPreferences)
```

## 颜色 / 字体
- 背景：#FFF8F0（暖米白）
- 主色：#FF7043（深橙/珊瑚）
- 强调：#FFA726（琥珀橙）
- 正文：18sp，标题：22sp，按钮：20sp，Bold
- 悬浮球直径：56dp，带阴影

## 悬浮球状态机
IDLE(收起) ──点击──> EXPANDED(展开卡片)
EXPANDED ──收起/关闭──> IDLE
任意状态 ──拖拽──> 移动位置

## 步骤卡内容结构
```
[教程名] 第 X / N 步
[步骤标题]
[详细说明：在哪个App，点哪里，看到什么]
[我做到了] [上一步] [再说一遍] [暂停] [关闭]
```

## 教程数据格式 (JSON)
```json
{
  "id": "wechat_font",
  "title": "微信调大字体",
  "steps": [
    { "title": "打开微信", "desc": "在桌面找到绿色的微信图标，用手指点一下。看到微信首页就成功了。" },
    ...
  ]
}
```

## 导入格式（纯文本）
```
教程名称：XXX
第1步：标题 | 说明
第2步：标题 | 说明
```
长度限制：教程名≤30字，步骤数 2–20，每步说明≤200字。
