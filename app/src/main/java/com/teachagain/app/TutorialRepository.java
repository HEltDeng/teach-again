package com.teachagain.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 教程仓库：内置 3 个模板 + 家人创建/导入的教程（存本机 JSON 文件）。
 * 全部离线、本机存储。
 */
public class TutorialRepository {

    private static final String USER_FILE = "user_tutorials.json";
    private final Context appContext;

    public TutorialRepository(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    /** 返回全部教程：内置 + 用户的。 */
    public List<Tutorial> getAll() {
        List<Tutorial> list = new ArrayList<>(builtinTutorials());
        list.addAll(loadUserTutorials());
        return list;
    }

    public Tutorial getById(String id) {
        for (Tutorial t : getAll()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    // ---------- 内置教程 ----------

    public static List<Tutorial> builtinTutorials() {
        List<Tutorial> list = new ArrayList<>();

        List<Tutorial.Step> fontSteps = new ArrayList<>();
        fontSteps.add(new Tutorial.Step("打开微信",
                "在手机桌面找到绿色的「微信」图标，用手指轻轻点一下。看到满屏聊天列表就打开成功啦。"));
        fontSteps.add(new Tutorial.Step("点右下角「我」",
                "看屏幕最下面一排，最右边有个「我」字，点它一下。"));
        fontSteps.add(new Tutorial.Step("点「设置」",
                "在这个页面往下找，看到带小齿轮的「设置」两个字，点一下。"));
        fontSteps.add(new Tutorial.Step("点「界面与显示」",
                "在微信的「设置」页面找到「界面与显示」，点进去。这里的「通用」可能只是分组标题，不用点。部分旧版微信需要先进入「通用」。"));
        fontSteps.add(new Tutorial.Step("点「字体大小」",
                "在「界面与显示」页面找到「字体大小」，点一下。若您的版本显示「字号大小」，也是调整文字大小的入口。找不到时，点小帮手里的「找不到」，先别着急。"));
        fontSteps.add(new Tutorial.Step("把滑块往右拖",
                "屏幕下方有一条横线和一个小圆点，用手指按住小圆点往右边慢慢拖，上面的字就变大了。调到您看着舒服就行。"));
        fontSteps.add(new Tutorial.Step("点右上角「完成」",
                "满意了就点右上角的「完成」，字体就变大保存好啦。"));
        list.add(new Tutorial("wechat_font", "微信调大字体", fontSteps, true));

        List<Tutorial.Step> photoSteps = new ArrayList<>();
        photoSteps.add(new Tutorial.Step("打开微信",
                "在桌面点绿色的「微信」图标。"));
        photoSteps.add(new Tutorial.Step("找到要发的人",
                "在聊天列表里找到想发照片的家人，比如「女儿」，点进去打开聊天。"));
        photoSteps.add(new Tutorial.Step("点输入框右边的「+」",
                "在最下面输入文字的方框右边，有个圆圈里的「+」号，点它一下。"));
        photoSteps.add(new Tutorial.Step("点「相册」",
                "弹出来一排小图标，找到「相册」，点一下。"));
        photoSteps.add(new Tutorial.Step("选一张照片",
                "看到手机里的照片，点一下您想发的那张，右上角会出现一个对勾。"));
        photoSteps.add(new Tutorial.Step("点「发送」",
                "点右下角的「发送」，照片就发出去啦。聊天里能看到照片就成功了。"));
        list.add(new Tutorial("wechat_photo", "给家人发照片", photoSteps, true));

        List<Tutorial.Step> videoSteps = new ArrayList<>();
        videoSteps.add(new Tutorial.Step("打开微信",
                "在桌面点绿色的「微信」图标。"));
        videoSteps.add(new Tutorial.Step("找到要打给谁",
                "在聊天列表里点开想视频的家人的聊天。"));
        videoSteps.add(new Tutorial.Step("点输入框右边的「+」",
                "在聊天页面最下面，输入文字方框的右边，有个圆圈里的「+」号，点它一下，会弹出一排功能。"));
        videoSteps.add(new Tutorial.Step("点「视频通话」",
                "在弹出的那排功能里找到「视频通话」，点一下。"));
        videoSteps.add(new Tutorial.Step("选「视频通话」",
                "如果问您是语音还是视频，选「视频通话」。然后等对方接听。"));
        videoSteps.add(new Tutorial.Step("接通后就能看到对方",
                "对方接了，屏幕上就能看到人、听到声音啦。想挂断就点红色的电话图标。"));
        list.add(new Tutorial("wechat_video", "发起视频通话", videoSteps, true));

        return list;
    }

    // ---------- 用户教程读写 ----------

    public synchronized List<Tutorial> loadUserTutorials() {
        List<Tutorial> list = new ArrayList<>();
        File f = new File(appContext.getFilesDir(), USER_FILE);
        if (!f.exists()) return list;
        try {
            String content = readFile(f);
            JSONArray arr = new JSONArray(content);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Tutorial.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            // 文件损坏时返回空，不崩溃
        }
        return list;
    }

    /** 保存成功返回 true；JSON 构建或磁盘写入失败都如实返回 false。 */
    public synchronized boolean saveUserTutorial(Tutorial t) {
        try {
            List<Tutorial> existing = loadUserTutorials();
            // 相同 id 覆盖
            List<Tutorial> merged = new ArrayList<>();
            boolean replaced = false;
            for (Tutorial e : existing) {
                if (e.id.equals(t.id)) {
                    merged.add(t);
                    replaced = true;
                } else {
                    merged.add(e);
                }
            }
            if (!replaced) merged.add(t);

            JSONArray arr = new JSONArray();
            for (Tutorial e : merged) arr.put(e.toJson());
            // writeFile 抛异常即视为保存失败
            writeFile(new File(appContext.getFilesDir(), USER_FILE), arr.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized boolean deleteUserTutorial(String id) {
        try {
            List<Tutorial> existing = loadUserTutorials();
            JSONArray arr = new JSONArray();
            for (Tutorial e : existing) {
                if (!e.id.equals(id)) arr.put(e.toJson());
            }
            writeFile(new File(appContext.getFilesDir(), USER_FILE), arr.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String readFile(File f) throws Exception {
        byte[] data = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int read = in.read(data);
            if (read <= 0) return "[]";
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /** 写入失败向上抛出，调用方据此如实报告。 */
    private void writeFile(File f, String content) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
