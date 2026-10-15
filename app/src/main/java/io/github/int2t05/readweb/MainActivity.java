package io.github.int2t05.readweb;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.webkit.WebViewAssetLoader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends ComponentActivity {
    private static final String READER_URL = "https://appassets.androidplatform.net/assets/reader.html";
    private static final int MAX_RESIDENT_WEBVIEWS = 2;

    private final LinkedHashMap<Long, WebView> residentViews = new LinkedHashMap<>(4, .75f, true);
    private final Map<Long, Future<?>> markdownJobs = new LinkedHashMap<>();
    private final Map<Long, AtomicLong> markdownGenerations = new LinkedHashMap<>();
    private final Map<Long, String> markdownSources = new LinkedHashMap<>();
    private final Set<Long> markdownFailures = new HashSet<>();
    // 仅主线程维护，标记内置阅读页的脚本是否已就绪。
    private final Set<WebView> readyReaderViews = Collections.newSetFromMap(new WeakHashMap<>());
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "readweb-markdown-fetch");
        thread.setDaemon(true);
        return thread;
    });
    private final MarkdownFetcher markdownFetcher = new MarkdownFetcher();

    private TabCollection tabs;
    private SessionStore sessionStore;
    private BookmarkCollection bookmarks;
    private BookmarkStore bookmarkStore;
    private WebViewAssetLoader assetLoader;
    private LinearLayout root;
    private FrameLayout screenRoot;
    private EditText address;
    private ProgressBar progress;
    private FrameLayout pageHost;
    private TextView emptyMessage;
    private ReaderToolsPanel toolsPanel;
    private boolean toolsOpen;
    private int readerFontSize = 18;
    private int readingProgressPercent;
    private long readingProgressTabId = Long.MIN_VALUE;
    private boolean progressUpdatePending;
    private boolean recoveringRenderer;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionStore = new SessionStore(this);
        tabs = sessionStore.load();
        if (tabs.size() == 0) tabs.newTab("");
        bookmarkStore = new BookmarkStore(this);
        bookmarks = bookmarkStore.load();
        readerFontSize = getSharedPreferences("readweb_settings", MODE_PRIVATE)
                .getInt("markdown_font_size", 18);
        readerFontSize = Math.max(14, Math.min(28, readerFontSize));
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        CookieManager.getInstance().setAcceptCookie(true);
        buildUi();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (toolsOpen) {
                    hideTools();
                    return;
                }
                WebView view = activeWebView();
                if (view != null && view.canGoBack()) {
                    view.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
        showActiveTab();
    }

    private void buildUi() {
        screenRoot = new FrameLayout(this);
        configureSystemBars(screenRoot);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(isNightMode() ? 0xff181a18 : 0xfff6f7f4);

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(dp(10), dp(8), dp(10), dp(6));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(15);
        address.setHint(R.string.address_hint);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setSelectAllOnFocus(true);
        address.setOnEditorActionListener((view, action, event) -> {
            boolean go = action == EditorInfo.IME_ACTION_GO || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP);
            if (go) openAddress();
            return go;
        });
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addressRow.addView(button(getString(R.string.open), getString(R.string.open), this::openAddress),
                new LinearLayout.LayoutParams(dp(64), dp(44)));
        root.addView(addressRow);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setContentDescription(getString(R.string.loading));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        pageHost = new FrameLayout(this);
        emptyMessage = new TextView(this);
        emptyMessage.setText(R.string.empty_message);
        emptyMessage.setTextColor(isNightMode() ? 0xffa2aaa1 : 0xff657067);
        emptyMessage.setTextSize(16);
        emptyMessage.setGravity(Gravity.CENTER);
        emptyMessage.setPadding(dp(32), dp(32), dp(32), dp(32));
        pageHost.addView(emptyMessage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(6), dp(4), dp(6), dp(4));
        actions.addView(button("‹", getString(R.string.back), this::goBack));
        actions.addView(button("›", getString(R.string.forward), this::goForward));
        actions.addView(button("⟳", getString(R.string.refresh), this::reload));
        actions.addView(button(getString(R.string.tools), this::toggleTools));
        actions.addView(button(getString(R.string.tabs), this::showTabPicker));
        actions.addView(button("＋", getString(R.string.new_tab), this::newTab));
        actions.addView(button("×", getString(R.string.close_tab), this::closeActiveTab));
        actionsScroll.addView(actions);
        root.addView(actionsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        screenRoot.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildToolsPanel();
        setContentView(screenRoot);
    }

    private void buildToolsPanel() {
        toolsPanel = new ReaderToolsPanel(this, isNightMode(), new ReaderToolsPanel.Actions() {
            @Override public void onBookmark() { toggleBookmark(); }
            @Override public void onShowBookmarks() { showBookmarks(); }
            @Override public void onReadAsMarkdown() { readAsMarkdown(); }
            @Override public void onFontChange(int delta) { adjustMarkdownFont(delta); }
            @Override public void onNavigateToHeading(int index) {
                WebView view = activeWebView();
                if (view != null && isReaderUrl(view.getUrl())) {
                    view.evaluateJavascript("window.ReadWebMarkdownDocument.scrollToHeading(" + index + ");", null);
                }
            }
        }, () -> toolsOpen = false);
        screenRoot.addView(toolsPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void toggleTools() {
        if (toolsOpen) hideTools();
        else showTools();
    }

    private void showTools() {
        toolsOpen = true;
        toolsPanel.setOpen(true);
        updateToolsPanel();
        updateOutline();
    }

    private void hideTools() {
        toolsOpen = false;
        if (toolsPanel != null) toolsPanel.setOpen(false);
    }

    private void updateToolsPanel() {
        if (toolsPanel == null) return;
        TabSession active = tabs == null ? null : tabs.active();
        long activeId = active == null ? Long.MIN_VALUE : active.id();
        if (activeId != readingProgressTabId) {
            readingProgressTabId = activeId;
            readingProgressPercent = 0;
        }
        Bookmark saved = active == null ? null : bookmarks.find(active.url());
        WebView view = activeWebView();
        boolean hasMarkdown = active != null && active.isMarkdownReader()
                && view != null && isReaderUrl(view.getUrl());
        String title = active == null || active.url().isEmpty() ? getString(R.string.no_page)
                : (active.title().isEmpty() ? active.url() : active.title());
        toolsPanel.bind(title, saved != null, active != null && !active.url().isEmpty(),
                active != null && !active.url().isEmpty(), readerFontSize,
                hasMarkdown && readerFontSize > 14, hasMarkdown && readerFontSize < 28,
                readingProgressPercent);
        if (!hasMarkdown) {
            toolsPanel.showOutline(java.util.Collections.emptyList(), active == null || active.url().isEmpty()
                    ? getString(R.string.outline_empty) : getString(R.string.outline_markdown_only));
        }
        if (toolsOpen) updateReadingProgress();
    }

    private void updateReadingProgress() {
        if (!toolsOpen || toolsPanel == null) return;
        WebView view = activeWebView();
        if (view == null) {
            readingProgressPercent = 0;
            toolsPanel.updateProgress(readingProgressPercent);
            return;
        }
        if (progressUpdatePending) return;
        progressUpdatePending = true;
        long tabId = tabs.activeId();
        // 以页面 CSS 像素计算进度，短页和普通网页共用同一规则。
        String script = "(function(){var root=document.documentElement;var body=document.body;"
                + "return [Math.max(0,Math.round(window.scrollY||0)),"
                + "Math.max(root?root.scrollHeight:0,body?body.scrollHeight:0),"
                + "Math.max(0,Math.round(window.innerHeight||(root?root.clientHeight:0)))];})()";
        toolsPanel.postDelayed(() -> {
            if (!toolsOpen) {
                progressUpdatePending = false;
                return;
            }
            if (tabs.activeId() != tabId || activeWebView() != view) {
                progressUpdatePending = false;
                updateReadingProgress();
                return;
            }
            view.evaluateJavascript(script, result -> {
                progressUpdatePending = false;
                if (!toolsOpen || tabs.activeId() != tabId || activeWebView() != view) {
                    updateReadingProgress();
                    return;
                }
                try {
                    JSONArray metrics = new JSONArray(result);
                    readingProgressPercent = ReadingProgress.percent(metrics.getInt(0), metrics.getInt(1),
                            1f, metrics.getInt(2));
                } catch (JSONException error) {
                    readingProgressPercent = 0;
                }
                toolsPanel.updateProgress(readingProgressPercent);
            });
        }, 150);
    }

    private void updateOutline() {
        if (!toolsOpen || toolsPanel == null) return;
        TabSession active = tabs.active();
        WebView view = activeWebView();
        if (active == null || !active.isMarkdownReader() || view == null || !isReaderUrl(view.getUrl())) {
            updateToolsPanel();
            return;
        }
        long tabId = active.id();
        view.evaluateJavascript("window.ReadWebMarkdownDocument.getOutline();", result -> {
            if (!toolsOpen || tabs.activeId() != tabId || activeWebView() != view
                    || !isReaderUrl(view.getUrl()) || result == null || "null".equals(result)) return;
            try {
                JSONArray headings = new JSONArray(result);
                ArrayList<ReaderToolsPanel.OutlineItem> outline = new ArrayList<>();
                for (int i = 0; i < headings.length(); i++) {
                    JSONObject item = headings.optJSONObject(i);
                    if (item == null) continue;
                    int level = Math.max(1, Math.min(6, item.optInt("level", 1)));
                    String title = item.optString("title", "").trim();
                    if (title.isEmpty()) continue;
                    outline.add(new ReaderToolsPanel.OutlineItem(i, level, title));
                }
                toolsPanel.showOutline(outline, getString(R.string.outline_empty));
            } catch (JSONException ignored) {
                toolsPanel.showOutline(java.util.Collections.emptyList(), getString(R.string.outline_unavailable));
            }
        });
    }

    private void toggleBookmark() {
        TabSession active = tabs.active();
        if (active == null || active.url().isEmpty()) return;
        Bookmark existing = bookmarks.find(active.url());
        if (existing != null) {
            bookmarks.remove(existing.url());
        } else if (!bookmarks.add(active.url(), active.title())) {
            toast(R.string.bookmark_limit_or_invalid);
            return;
        }
        bookmarkStore.save(bookmarks);
        updateToolsPanel();
    }

    private void showBookmarks() {
        hideTools();
        ArrayList<Bookmark> items = new ArrayList<>(bookmarks.all());
        if (items.isEmpty()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.bookmarks)
                    .setMessage(R.string.bookmarks_empty)
                    .setPositiveButton(R.string.close, null)
                    .show();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        scroll.addView(list);
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.bookmarks)
                .setView(scroll)
                .setNegativeButton(R.string.close, null)
                .create();
        for (Bookmark bookmark : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(this);
            title.setText(bookmark.title());
            title.setTextSize(15);
            title.setTextColor(isNightMode() ? 0xffe5e9e3 : 0xff252923);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView url = new TextView(this);
            url.setText(bookmark.url());
            url.setTextSize(12);
            url.setTextColor(isNightMode() ? 0xffa2aaa1 : 0xff657067);
            url.setMaxLines(1);
            url.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(title);
            details.addView(url);
            details.setOnClickListener(view -> {
                dialog.dismiss();
                openBookmark(bookmark);
            });
            row.addView(details, new LinearLayout.LayoutParams(0, dp(64), 1f));
            Button remove = button(getString(R.string.remove), () -> {
                bookmarks.remove(bookmark.url());
                bookmarkStore.save(bookmarks);
                dialog.dismiss();
                updateToolsPanel();
                showBookmarks();
            });
            row.addView(remove);
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        dialog.show();
    }

    private void openBookmark(Bookmark bookmark) {
        hideTools();
        address.setText(bookmark.url());
        openAddress();
    }

    private Button button(String label, Runnable action) {
        return button(label, label, action);
    }

    private Button button(String label, String accessibilityLabel, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setContentDescription(accessibilityLabel);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void configureSystemBars(View content) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT == 26) {
            window.setNavigationBarColor(0xff315f45);
        } else if (Build.VERSION.SDK_INT < 35) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (isNightMode()) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT < 35) return;
        window.setDecorFitsSystemWindows(false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void openAddress() {
        final URI uri;
        try {
            uri = UrlPolicy.normalize(address.getText().toString());
        } catch (IllegalArgumentException error) {
            toast(R.string.invalid_url);
            return;
        }
        hideKeyboard();
        TabSession tab = tabs.active();
        if (tab == null) tab = tabs.newTab("");
        invalidateMarkdownFetch(tab.id());
        markdownFailures.remove(tab.id());
        tab.updateUrl(uri.toString());
        tab.updateTitle(uri.toString());
        tab.updateScrollY(0);
        tab.setMarkdownReader(false);
        persist();
        address.setText(uri.toString());
        if (MarkdownUrl.isMarkdown(uri)) fetchMarkdown(tab, uri);
        else loadWebsite(tab, uri.toString());
    }

    private void loadWebsite(TabSession tab, String url) {
        WebView webView = getOrCreateWebView(tab);
        if (webView == null) return;
        readyReaderViews.remove(webView);
        tab.setMarkdownReader(false);
        tab.updateUrl(url);
        persist();
        if (tab.id() == tabs.activeId()) {
            address.setText(url);
            showWebView(webView);
        }
        webView.loadUrl(url);
        if (toolsOpen && tab.id() == tabs.activeId()) updateToolsPanel();
    }

    private void readAsMarkdown() {
        TabSession tab = tabs.active();
        if (tab == null || tab.url().trim().isEmpty()) {
            toast(R.string.invalid_url);
            return;
        }
        try {
            fetchMarkdown(tab, UrlPolicy.normalize(tab.url()));
        } catch (IllegalArgumentException error) {
            toast(R.string.invalid_url);
        }
    }

    private void fetchMarkdown(TabSession tab, URI uri) {
        cancelMarkdownJob(tab.id());
        markdownFailures.remove(tab.id());
        AtomicLong generation = markdownGenerations.computeIfAbsent(tab.id(), ignored -> new AtomicLong());
        long requestGeneration = generation.incrementAndGet();
        long tabId = tab.id();
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(20);
        tab.updateUrl(uri.toString());
        tab.updateTitle(uri.toString());
        tab.setMarkdownReader(true);
        address.setText(uri.toString());
        persist();
        if (toolsOpen && tabId == tabs.activeId()) updateToolsPanel();
        Future<?> job = networkExecutor.submit(() -> {
            try {
                MarkdownFetcher.Result result = markdownFetcher.fetch(uri);
                runOnUiThread(() -> {
                    if (!isCurrentRequest(tabId, requestGeneration)) return;
                    markdownJobs.remove(tabId);
                    progress.setVisibility(View.GONE);
                    TabSession current = tabs.find(tabId);
                    if (current == null) return;
                    current.updateUrl(result.finalUri.toString());
                    if (result.html) {
                        markdownFailures.remove(tabId);
                        current.setMarkdownReader(false);
                        loadWebsite(current, result.finalUri.toString());
                    } else {
                        markdownFailures.remove(tabId);
                        current.setMarkdownReader(true);
                        current.updateTitle(result.finalUri.toString());
                        persist();
                        if (current.id() == tabs.activeId()) address.setText(result.finalUri.toString());
                        WebView webView = residentViews.get(tabId);
                        if (current.id() == tabs.activeId()) {
                            if (webView == null) webView = getOrCreateWebView(current);
                            if (webView != null) showWebView(webView);
                        }
                        if (webView != null) loadMarkdownPage(webView, result.finalUri, result.markdown);
                    }
                    if (toolsOpen && current.id() == tabs.activeId()) updateToolsPanel();
                });
            } catch (IOException error) {
                runOnUiThread(() -> {
                    if (!isCurrentRequest(tabId, requestGeneration)) return;
                    markdownJobs.remove(tabId);
                    markdownFailures.add(tabId);
                    progress.setVisibility(View.GONE);
                    if (tabs.activeId() == tabId) {
                        showNativeMessage(getString(R.string.markdown_error));
                        if (toolsOpen) updateToolsPanel();
                    }
                });
            }
        });
        markdownJobs.put(tabId, job);
    }

    private boolean isCurrentRequest(long tabId, long generation) {
        AtomicLong current = markdownGenerations.get(tabId);
        return !isFinishing() && !isDestroyed() && current != null && current.get() == generation;
    }

    private void cancelMarkdownJob(long tabId) {
        Future<?> previous = markdownJobs.remove(tabId);
        if (previous != null) previous.cancel(true);
        if (progress != null && tabId == tabs.activeId() && markdownJobs.isEmpty()) {
            progress.setVisibility(View.GONE);
        }
    }

    private void invalidateMarkdownFetch(long tabId) {
        cancelMarkdownJob(tabId);
        markdownGenerations.computeIfAbsent(tabId, ignored -> new AtomicLong()).incrementAndGet();
    }

    private void loadMarkdownPage(WebView webView, URI source, String markdown) {
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        TabSession owner = findOwner(webView);
        if (owner == null) return;
        markdownSources.put(owner.id(), source.toString());
        if (readyReaderViews.contains(webView) && isReaderUrl(webView.getUrl())) {
            applyMarkdown(webView, source.toString(), encoded);
            return;
        }
        pendingMarkdownSource.put(webView, source.toString());
        pendingMarkdownBase64.put(webView, encoded);
        webView.loadUrl(READER_URL);
    }

    private final Map<WebView, String> pendingMarkdownSource = new java.util.WeakHashMap<>();
    private final Map<WebView, String> pendingMarkdownBase64 = new java.util.WeakHashMap<>();

    private void renderPendingMarkdown(WebView webView) {
        String source = pendingMarkdownSource.remove(webView);
        String encoded = pendingMarkdownBase64.remove(webView);
        if (source == null || encoded == null) return;
        applyMarkdown(webView, source, encoded);
    }

    private void applyMarkdown(WebView webView, String source, String encoded) {
        String javascript = "window.ReadWebMarkdownDocument.setBase64(" + JSONObject.quote(source) + ","
                + JSONObject.quote(encoded) + ");window.ReadWebMarkdownDocument.setFontSize(" + readerFontSize + ");";
        webView.evaluateJavascript(javascript, result -> {
            if (result != null && result.startsWith("\"TypeError") && tabs.active() != null) {
                toast(R.string.markdown_error);
            }
            TabSession owner = findOwner(webView);
            if (owner != null && owner.id() == tabs.activeId()) {
                updateToolsPanel();
                updateOutline();
            }
        });
        TabSession tab = findOwner(webView);
        if (tab != null && tab.scrollY() > 0) webView.post(() -> webView.scrollTo(0, tab.scrollY()));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView getOrCreateWebView(TabSession tab) {
        WebView existing = residentViews.get(tab.id());
        if (existing != null) return existing;
        while (residentViews.size() >= MAX_RESIDENT_WEBVIEWS) {
            Long eldestId = residentViews.keySet().iterator().next();
            if (eldestId == tab.id() && residentViews.size() > 1) {
                for (Long id : residentViews.keySet()) if (id != tab.id()) { eldestId = id; break; }
            }
            destroyWebView(eldestId);
        }

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setBackgroundColor(isNightMode() ? 0xff181a18 : Color.WHITE);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (tabs.active() != null && view == residentViews.get(tabs.active().id())) {
                    progress.setProgress(value);
                    progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override public void onReceivedTitle(WebView view, String title) {
                TabSession owner = findOwner(view);
                if (owner != null) {
                    owner.updateTitle(title);
                    persist();
                }
            }

            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                                    Message resultMessage) {
                if (!isUserGesture || !(resultMessage.obj instanceof WebView.WebViewTransport transport)) {
                    return false;
                }
                saveActiveScroll();
                final TabSession tab;
                try {
                    tab = tabs.newTab("");
                } catch (IllegalStateException error) {
                    toast(R.string.tab_limit);
                    return false;
                }
                persist();
                WebView child = getOrCreateWebView(tab);
                showWebView(child);
                address.setText("");
                transport.setWebView(child);
                resultMessage.sendToTarget();
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return interceptNavigation(view, request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if ("about:blank".equals(url)) return;
                TabSession owner = findOwner(view);
                if (owner != null && owner.id() == tabs.activeId()) readingProgressPercent = 0;
                if (owner != null && isReaderUrl(url)) {
                    readyReaderViews.remove(view);
                    String source = markdownSources.get(owner.id());
                    if (source != null) {
                        owner.setMarkdownReader(true);
                        owner.updateUrl(source);
                        if (owner.id() == tabs.activeId()) address.setText(source);
                        persist();
                    }
                } else if (owner != null && !isLocalAssetUrl(url)) {
                    readyReaderViews.remove(view);
                    if (owner.isMarkdownReader()) invalidateMarkdownFetch(owner.id());
                    owner.setMarkdownReader(false);
                    owner.updateUrl(url);
                    if (owner.id() == tabs.activeId()) address.setText(url);
                    persist();
                }
                if (owner != null && owner.id() == tabs.activeId() && toolsOpen) updateToolsPanel();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                TabSession owner = findOwner(view);
                if (isReaderUrl(url)) {
                    readyReaderViews.add(view);
                    if (pendingMarkdownSource.containsKey(view)) {
                        renderPendingMarkdown(view);
                    } else if (owner != null) {
                        String source = markdownSources.get(owner.id());
                        if (source != null) fetchMarkdown(owner, safeUri(source));
                    }
                }
                if (owner != null && owner.id() == tabs.activeId() && owner.scrollY() > 0
                        && !owner.isMarkdownReader()) {
                    view.post(() -> view.scrollTo(0, owner.scrollY()));
                }
                if (owner != null && owner.id() == tabs.activeId()) {
                    view.post(() -> {
                        if (owner.id() == tabs.activeId()) {
                            updateReadingProgress();
                            if (toolsOpen) updateToolsPanel();
                        }
                    });
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    TabSession owner = findOwner(view);
                    if (owner != null && owner.id() == tabs.activeId()) toast(R.string.load_error);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                TabSession owner = findOwner(view);
                if (owner != null && owner.id() == tabs.activeId()) toast(R.string.load_error);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                if (recoveringRenderer) return true;
                recoveringRenderer = true;
                ArrayList<Long> ids = new ArrayList<>(residentViews.keySet());
                for (Long id : ids) destroyWebView(id);
                persist();
                pageHost.post(() -> {
                    recoveringRenderer = false;
                    showActiveTab();
                    toast(R.string.load_error);
                });
                return true;
            }
        });
        webView.setOnLongClickListener(view -> {
            WebView.HitTestResult hit = webView.getHitTestResult();
            if (hit == null || (hit.getType() != WebView.HitTestResult.ANCHOR_TYPE
                    && hit.getType() != WebView.HitTestResult.SRC_ANCHOR_TYPE)) return false;
            openInNewTab(hit.getExtra());
            return true;
        });
        webView.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            TabSession owner = findOwner(webView);
            if (owner == null) return;
            owner.updateScrollY(scrollY);
            if (owner.id() == tabs.activeId() && toolsOpen) updateReadingProgress();
        });
        residentViews.put(tab.id(), webView);
        return webView;
    }

    private boolean interceptNavigation(WebView view, String value) {
        if (isLocalAssetUrl(value)) {
            TabSession owner = findOwner(view);
            return owner == null || (!owner.isMarkdownReader() && !isReaderUrl(view.getUrl()));
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (Exception error) {
            return true;
        }
        String scheme = uri.getScheme();
        if (scheme != null && scheme.equalsIgnoreCase("mailto")) {
            openExternal(value);
            return true;
        }
        if (scheme != null && scheme.equalsIgnoreCase("javascript")) {
            TabSession owner = findOwner(view);
            return owner == null || owner.isMarkdownReader();
        }
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return true;
        }
        TabSession owner = findOwner(view);
        if (owner == null || !uri.isAbsolute()) return true;
        if (MarkdownUrl.isMarkdown(uri)) {
            owner.updateScrollY(0);
            owner.updateUrl(uri.toString());
            owner.setMarkdownReader(false);
            markdownFailures.remove(owner.id());
            if (owner.id() == tabs.activeId()) address.setText(uri.toString());
            fetchMarkdown(owner, uri);
            return true;
        }
        readyReaderViews.remove(view);
        owner.setMarkdownReader(false);
        invalidateMarkdownFetch(owner.id());
        markdownFailures.remove(owner.id());
        if (!uri.toString().equals(owner.url())) owner.updateScrollY(0);
        owner.updateUrl(uri.toString());
        if (owner.id() == tabs.activeId()) address.setText(uri.toString());
        persist();
        return false;
    }

    private TabSession findOwner(WebView webView) {
        for (Map.Entry<Long, WebView> entry : residentViews.entrySet()) {
            if (entry.getValue() == webView) return tabs.find(entry.getKey());
        }
        return null;
    }

    private boolean isLocalAssetUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        return "https".equalsIgnoreCase(uri.getScheme())
                && "appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())
                && uri.getPort() == -1;
    }

    private boolean isReaderUrl(String value) {
        return READER_URL.equals(value);
    }

    private void showActiveTab() {
        TabSession active = tabs.active();
        if (active == null) {
            showNativeMessage(getString(R.string.empty_message));
            address.setText("");
            persist();
            if (toolsOpen) updateToolsPanel();
            return;
        }
        address.setText(active.url());
        WebView existing = residentViews.get(active.id());
        if (active.url().trim().isEmpty()) {
            if (existing != null) {
                active.updateScrollY(existing.getScrollY());
                pageHost.removeView(existing);
            }
            showNativeMessage(getString(R.string.empty_message));
            if (toolsOpen) updateToolsPanel();
            return;
        }
        if (active.isMarkdownReader()) {
            WebView currentView = residentViews.get(active.id());
            if (markdownFailures.contains(active.id())) {
                showNativeMessage(getString(R.string.markdown_error));
                if (toolsOpen) updateToolsPanel();
                return;
            }
            if ((currentView == null || !isReaderUrl(currentView.getUrl()))
                    && !markdownJobs.containsKey(active.id())) {
                showNativeMessage(getString(R.string.loading));
                fetchMarkdown(active, safeUri(active.url()));
                if (toolsOpen) updateToolsPanel();
                return;
            }
        }
        WebView webView = getOrCreateWebView(active);
        if (webView == null) return;
        showWebView(webView);
        if (!active.isMarkdownReader() && (existing == null || webView.getUrl() == null)) {
            webView.loadUrl(active.url());
        }
        if (toolsOpen) {
            updateToolsPanel();
            updateOutline();
        }
    }

    private void showNativeMessage(CharSequence message) {
        emptyMessage.setText(message);
        if (emptyMessage.getParent() instanceof ViewGroup parent) parent.removeView(emptyMessage);
        pageHost.removeAllViews();
        pageHost.addView(emptyMessage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private URI safeUri(String value) {
        try { return UrlPolicy.normalize(value); }
        catch (IllegalArgumentException error) { return URI.create("https://invalid.local/"); }
    }

    private void showWebView(WebView webView) {
        if (webView.getParent() instanceof ViewGroup parent) parent.removeView(webView);
        pageHost.removeAllViews();
        pageHost.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void newTab() {
        try {
            saveActiveScroll();
            tabs.newTab("");
        } catch (IllegalStateException error) {
            toast(R.string.tab_limit);
            return;
        }
        persist();
        showActiveTab();
        address.requestFocus();
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(address,
                InputMethodManager.SHOW_IMPLICIT);
    }

    private void openInNewTab(String value) {
        if (value == null || value.trim().isEmpty()) return;
        final URI uri;
        try { uri = UrlPolicy.normalize(value); }
        catch (IllegalArgumentException error) { return; }
        try {
            saveActiveScroll();
            TabSession tab = tabs.newTab(uri.toString());
            persist();
            if (MarkdownUrl.isMarkdown(uri)) {
                pageHost.removeAllViews();
                pageHost.addView(emptyMessage);
                fetchMarkdown(tab, uri);
            }
            else loadWebsite(tab, uri.toString());
        } catch (IllegalStateException error) {
            toast(R.string.tab_limit);
        }
    }

    private void closeActiveTab() {
        TabSession active = tabs.active();
        if (active == null) return;
        cancelMarkdownJob(active.id());
        markdownGenerations.remove(active.id());
        markdownSources.remove(active.id());
        markdownFailures.remove(active.id());
        destroyWebView(active.id());
        tabs.close(active.id());
        if (tabs.size() == 0) tabs.newTab("");
        persist();
        showActiveTab();
    }

    private void showTabPicker() {
        if (tabs.size() == 0) return;
        ArrayList<TabSession> items = new ArrayList<>(tabs.all());
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            TabSession tab = items.get(i);
            labels[i] = (tab.title().trim().isEmpty() ? tab.url() : tab.title()) + "\n" + tab.url();
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.tabs)
                .setSingleChoiceItems(labels, indexOfActive(items), (dialog, which) -> {
                    saveActiveScroll();
                    tabs.select(items.get(which).id());
                    dialog.dismiss();
                    persist();
                    showActiveTab();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int indexOfActive(ArrayList<TabSession> values) {
        for (int i = 0; i < values.size(); i++) if (values.get(i).id() == tabs.activeId()) return i;
        return -1;
    }

    private void goBack() {
        WebView view = activeWebView();
        if (view != null && view.canGoBack()) view.goBack();
    }

    private void goForward() {
        WebView view = activeWebView();
        if (view != null && view.canGoForward()) view.goForward();
    }

    private void reload() {
        TabSession active = tabs.active();
        if (active == null || active.url().trim().isEmpty()) return;
        if (active.isMarkdownReader()) fetchMarkdown(active, safeUri(active.url()));
        else {
            WebView view = activeWebView();
            if (view != null) view.reload();
        }
    }

    private void adjustMarkdownFont(int delta) {
        TabSession active = tabs.active();
        WebView view = activeWebView();
        if (active == null || !active.isMarkdownReader() || view == null || !isReaderUrl(view.getUrl())) return;
        int next = Math.max(14, Math.min(28, readerFontSize + delta));
        if (next == readerFontSize) return;
        readerFontSize = next;
        getSharedPreferences("readweb_settings", MODE_PRIVATE).edit()
                .putInt("markdown_font_size", readerFontSize).apply();
        for (Map.Entry<Long, WebView> entry : residentViews.entrySet()) {
            TabSession owner = tabs.find(entry.getKey());
            if (owner != null && owner.isMarkdownReader() && isReaderUrl(entry.getValue().getUrl())) {
                entry.getValue().evaluateJavascript(
                        "window.ReadWebMarkdownDocument.setFontSize(" + readerFontSize + ");", null);
            }
        }
        updateToolsPanel();
    }

    private WebView activeWebView() {
        return tabs.active() == null ? null : residentViews.get(tabs.active().id());
    }

    private void saveActiveScroll() {
        TabSession active = tabs == null ? null : tabs.active();
        WebView view = activeWebView();
        if (active != null && view != null) active.updateScrollY(view.getScrollY());
    }

    private void openExternal(String value) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value))); }
        catch (ActivityNotFoundException error) { toast(R.string.load_error); }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(focus.getWindowToken(), 0);
        address.clearFocus();
    }

    private void persist() {
        if (tabs != null) sessionStore.save(tabs);
    }

    private void toast(int text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }

    private void destroyWebView(Long id) {
        WebView webView = residentViews.remove(id);
        if (webView == null) return;
        TabSession tab = tabs == null ? null : tabs.find(id);
        if (tab != null) tab.updateScrollY(webView.getScrollY());
        pendingMarkdownSource.remove(webView);
        pendingMarkdownBase64.remove(webView);
        readyReaderViews.remove(webView);
        if (webView.getParent() instanceof ViewGroup parent) parent.removeView(webView);
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();
        webView.removeAllViews();
        webView.destroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW && residentViews.size() > 1) {
            Long activeId = tabs.activeId();
            for (Long id : new ArrayList<>(residentViews.keySet())) {
                if (id != activeId) destroyWebView(id);
            }
            persist();
        }
    }

    @Override
    protected void onPause() {
        TabSession active = tabs == null ? null : tabs.active();
        WebView view = activeWebView();
        if (active != null && view != null) active.updateScrollY(view.getScrollY());
        persist();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        for (Long id : new ArrayList<>(residentViews.keySet())) destroyWebView(id);
        persist();
        for (Future<?> job : markdownJobs.values()) job.cancel(true);
        markdownJobs.clear();
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
