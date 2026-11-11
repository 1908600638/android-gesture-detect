package com.shizi.gesture.scan;

import android.graphics.Bitmap;

import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.mlsdk.gesture.MLGesture;
import com.huawei.hms.mlsdk.gesture.MLGestureAnalyzer;
import com.huawei.hms.mlsdk.gesture.MLGestureAnalyzerFactory;
import com.huawei.hms.mlsdk.gesture.MLGestureAnalyzerSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 手势识别：华为 HMS 的端侧手势检测，一次给出手势框、类别和概率。
 */
public final class GestureEngine implements Scanner {

    private static final MLGestureAnalyzerSetting SETTING =
            new MLGestureAnalyzerSetting.Factory().create();

    private final MLGestureAnalyzer analyzer =
            MLGestureAnalyzerFactory.getInstance().getGestureAnalyzer(SETTING);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private volatile boolean closed;

    @Override
    public void scan(final Bitmap bitmap, final Callback callback) {
        if (closed) {
            return;
        }
        worker.execute(() -> {
            MLFrame frame = new MLFrame.Creator().setBitmap(bitmap).create();
            analyzer.asyncAnalyseFrame(frame)
                    .addOnSuccessListener(gestures -> {
                        if (closed) {
                            return;
                        }
                        List<Detect> detects = new ArrayList<>();
                        for (MLGesture gesture : gestures) {
                            detects.add(new Detect(name(gesture.getCategory()), null,
                                    gesture.getScore(), gesture.getRect()));
                        }
                        callback.onResult(new ScanOutcome(detects, describe(detects)),
                                BoxPainter.paint(bitmap, detects));
                    })
                    .addOnFailureListener(error -> {
                        if (!closed) {
                            callback.onError(error);
                        }
                    });
        });
    }

    /** 0~8 对应数字 1~9，其余是常见手势。 */
    private static String name(int category) {
        if (category >= 0 && category <= 8) {
            return "数字 " + (category + 1);
        }
        switch (category) {
            case 9:
                return "差评";
            case 10:
                return "拳头";
            case 11:
                return "点赞";
            case 12:
                return "单手比心";
            case 13:
                return "OK";
            default:
                return "其他手势";
        }
    }

    private static String describe(List<Detect> detects) {
        if (detects.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < detects.size(); i++) {
            Detect detect = detects.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(i + 1).append(". ").append(detect.label);
            if (detect.score > 0f) {
                builder.append("   概率 ")
                        .append(String.format(Locale.US, "%.2f", detect.score));
            }
        }
        return builder.toString();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        worker.shutdownNow();
        try {
            analyzer.stop();
        } catch (Exception ignored) {
        }
    }
}
