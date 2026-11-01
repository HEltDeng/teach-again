package com.teachagain.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一个教程，含若干步骤。纯数据模型，可序列化为 JSON 存本机。 */
public class Tutorial {

    public static class Step {
        public final String title;   // 这一步做什么
        public final String desc;    // 在哪个App、点哪里、看到什么

        public Step(String title, String desc) {
            this.title = title;
            this.desc = desc;
        }
    }

    public final String id;
    public final String title;
    public final List<Step> steps;
    public final boolean builtin; // 内置模板 vs 家人创建

    public Tutorial(String id, String title, List<Step> steps, boolean builtin) {
        this.id = id;
        this.title = title;
        this.steps = steps;
        this.builtin = builtin;
    }

    /** Independent of clock precision; existing persisted IDs remain unchanged. */
    public static String newUserId() {
        return "user_" + UUID.randomUUID();
    }

    public int stepCount() {
        return steps.size();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("builtin", builtin);
        JSONArray arr = new JSONArray();
        for (Step s : steps) {
            JSONObject so = new JSONObject();
            so.put("title", s.title);
            so.put("desc", s.desc);
            arr.put(so);
        }
        o.put("steps", arr);
        return o;
    }

    public static Tutorial fromJson(JSONObject o) throws JSONException {
        String id = o.getString("id");
        String title = o.getString("title");
        boolean builtin = o.optBoolean("builtin", false);
        List<Step> steps = new ArrayList<>();
        JSONArray arr = o.getJSONArray("steps");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject so = arr.getJSONObject(i);
            steps.add(new Step(so.getString("title"), so.getString("desc")));
        }
        return new Tutorial(id, title, steps, builtin);
    }
}
