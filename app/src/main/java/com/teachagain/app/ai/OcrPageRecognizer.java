package com.teachagain.app.ai;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 只读窗口 OCR：截取"目标窗口"的画面，用 MLKit 中文模型识别可见文字行。
 *
 * 严格约束（隐私/合规）：
 * - 只截指定窗口（当前微信），用 AccessibilityService.takeScreenshotOfWindow。
 * - 本机识别，捆绑模型，不联网。
 * - 不保存截图、不打印识别文字、不上传；用完即释放 Bitmap/HardwareBuffer。
 * - 单任务 + 最小间隔 2 秒，防止频繁截屏。失败回调空列表。回调在主线程。
 *
 * 依据：
 * - developer.android.com/reference/android/accessibilityservice/AccessibilityService
 * - developers.google.com/ml-kit/vision/text-recognition/v2/android
 */
public class OcrPageRecognizer {

    private static final long MIN_INTERVAL_MS = 2000;

    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextRecognizer recognizer;

    private volatile boolean busy = false;
    private volatile long lastRunAt = 0;
    private volatile boolean closed = false;

    public OcrPageRecognizer(AccessibilityService service) {
        this.service = service;
        this.recognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
    }

    /**
     * 截取并识别指定窗口文字。结果（去重的行文本）回调到主线程；失败回空列表。
     */
    public void recognizeWindow(int windowId,
                                final Consumer<List<String>> callback) {
        if (closed) { postEmpty(callback); return; }
        // 需要 Android 14（API 34）的按窗口截图能力
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            postEmpty(callback);
            return;
        }
        long now = System.currentTimeMillis();
        if (busy || (now - lastRunAt) < MIN_INTERVAL_MS) {
            postEmpty(callback);
            return;
        }
        busy = true;
        lastRunAt = now;

        try {
            service.takeScreenshotOfWindow(windowId, service.getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(@NonNull AccessibilityService.ScreenshotResult result) {
                            processScreenshot(result, callback);
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            busy = false;
                            postEmpty(callback);
                        }
                    });
        } catch (Throwable t) {
            busy = false;
            postEmpty(callback);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void processScreenshot(AccessibilityService.ScreenshotResult result,
                                   final Consumer<List<String>> callback) {
        Bitmap bitmap = null;
        HardwareBuffer buffer = null;
        try {
            buffer = result.getHardwareBuffer();
            Bitmap hw = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (hw == null) {
                releaseBuffer(buffer);
                busy = false;
                postEmpty(callback);
                return;
            }
            // 拷贝成可被 MLKit 读取的软件位图，随即释放硬件缓冲
            bitmap = hw.copy(Bitmap.Config.ARGB_8888, false);
            hw.recycle();
            releaseBuffer(buffer);
            buffer = null;

            if (bitmap == null) {
                busy = false;
                postEmpty(callback);
                return;
            }

            final Bitmap toRecycle = bitmap;
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(image)
                    .addOnSuccessListener(text -> {
                        List<String> lines = extractLines(text);
                        safeRecycle(toRecycle);
                        busy = false;
                        post(callback, lines);
                    })
                    .addOnFailureListener(e -> {
                        safeRecycle(toRecycle);
                        busy = false;
                        postEmpty(callback);
                    });
            bitmap = null; // 交给回调释放
        } catch (Throwable t) {
            if (bitmap != null) safeRecycle(bitmap);
            releaseBuffer(buffer);
            busy = false;
            postEmpty(callback);
        }
    }

    private List<String> extractLines(Text text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        try {
            for (Text.TextBlock block : text.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    String s = line.getText();
                    if (s != null) {
                        s = s.trim();
                        if (!s.isEmpty() && s.length() <= 40 && !out.contains(s)) {
                            out.add(s);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private void releaseBuffer(HardwareBuffer buffer) {
        if (buffer != null) {
            try { buffer.close(); } catch (Throwable ignored) {}
        }
    }

    private void safeRecycle(Bitmap b) {
        if (b != null && !b.isRecycled()) {
            try { b.recycle(); } catch (Throwable ignored) {}
        }
    }

    private void post(Consumer<List<String>> cb, List<String> lines) {
        main.post(() -> {
            if (cb != null) cb.accept(lines);
        });
    }

    private void postEmpty(Consumer<List<String>> cb) {
        post(cb, new ArrayList<>());
    }

    /** 释放识别器资源。 */
    public void close() {
        closed = true;
        if (recognizer != null) {
            try { recognizer.close(); } catch (Throwable ignored) {}
            recognizer = null;
        }
    }
}
