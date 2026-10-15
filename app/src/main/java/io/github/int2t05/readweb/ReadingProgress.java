package io.github.int2t05.readweb;

/** 根据 WebView 页面与视口尺寸计算并限制滚动百分比。 */
public final class ReadingProgress {
    private ReadingProgress() {}

    public static int percent(int scrollY, int contentHeight, float scale, int viewportHeight) {
        if (contentHeight <= 0 || viewportHeight <= 0 || scale <= 0) return 0;
        int scrollRange = Math.round(contentHeight * scale) - viewportHeight;
        if (scrollRange <= 0) return 100;
        return Math.max(0, Math.min(100, Math.round(100f * Math.max(0, scrollY) / scrollRange)));
    }
}
