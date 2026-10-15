package io.github.int2t05.readweb;

import android.content.Context;
import android.graphics.Typeface;
import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** 提供收藏、字号、大纲与进度操作的左侧功能抽屉。 */
@SuppressLint("ViewConstructor")
public final class ReaderToolsPanel extends FrameLayout {
    public interface Actions {
        void onBookmark();
        void onShowBookmarks();
        void onReadAsMarkdown();
        void onFontChange(int delta);
        void onNavigateToHeading(int index);
    }

    public static final class OutlineItem {
        public final int index;
        public final int level;
        public final String title;

        public OutlineItem(int index, int level, String title) {
            this.index = index;
            this.level = level;
            this.title = title;
        }
    }

    private final View scrim;
    private final LinearLayout drawer;
    private final TextView pageTitle;
    private final Button bookmarkButton;
    private final Button markdownButton;
    private final Button decreaseFontButton;
    private final Button increaseFontButton;
    private final TextView fontSizeValue;
    private final TextView progressLabel;
    private final ProgressBar progressBar;
    private final LinearLayout outlineRows;
    private final TextView outlineEmpty;
    private final Actions actions;
    private final Runnable onDismiss;
    private final boolean nightMode;
    private boolean open;

    public ReaderToolsPanel(Context context, boolean nightMode, Actions actions, Runnable onDismiss) {
        super(context);
        this.nightMode = nightMode;
        this.actions = actions;
        this.onDismiss = onDismiss;

        scrim = new View(context);
        scrim.setBackgroundColor(0x80000000);
        scrim.setOnClickListener(view -> close());
        addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = new LinearLayout(context);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(16), dp(12), dp(16), dp(12));
        drawer.setBackgroundColor(nightMode ? 0xff202420 : 0xfffafbf8);
        drawer.setElevation(dp(8));
        int width = Math.min(dp(320), context.getResources().getDisplayMetrics().widthPixels - dp(48));
        addView(drawer, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(context.getString(R.string.tools), 20);
        heading.setTypeface(null, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1f));
        header.addView(button("×", context.getString(R.string.close_tools), this::close));
        drawer.addView(header);

        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        drawer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pageTitle = label("", 14);
        pageTitle.setTextColor(mutedColor());
        pageTitle.setMaxLines(2);
        pageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(pageTitle, itemParams());

        markdownButton = panelButton(context.getString(R.string.read_as_markdown), actions::onReadAsMarkdown);
        content.addView(markdownButton, itemParams());
        bookmarkButton = panelButton(context.getString(R.string.bookmark_add), actions::onBookmark);
        content.addView(bookmarkButton, itemParams());
        content.addView(panelButton(context.getString(R.string.bookmarks), actions::onShowBookmarks), itemParams());

        progressLabel = label(context.getString(R.string.reading_progress, 0), 14);
        content.addView(progressLabel, itemParams());
        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        content.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        TextView fontLabel = label(context.getString(R.string.font_size), 16);
        content.addView(fontLabel, itemParams());
        LinearLayout fontControls = new LinearLayout(context);
        fontControls.setGravity(Gravity.CENTER_VERTICAL);
        decreaseFontButton = panelButton("A−", context.getString(R.string.decrease_font),
                () -> actions.onFontChange(-1));
        increaseFontButton = panelButton("A+", context.getString(R.string.increase_font),
                () -> actions.onFontChange(1));
        fontSizeValue = label(context.getString(R.string.font_size_value, 18), 14);
        fontSizeValue.setGravity(Gravity.CENTER);
        fontControls.addView(decreaseFontButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        fontControls.addView(fontSizeValue, new LinearLayout.LayoutParams(0, dp(48), 1f));
        fontControls.addView(increaseFontButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        content.addView(fontControls, itemParams());

        TextView outlineTitle = label(context.getString(R.string.outline), 16);
        content.addView(outlineTitle, itemParams());
        outlineRows = new LinearLayout(context);
        outlineRows.setOrientation(LinearLayout.VERTICAL);
        content.addView(outlineRows, itemParams());
        outlineEmpty = label(context.getString(R.string.outline_empty), 14);
        outlineEmpty.setTextColor(mutedColor());
        content.addView(outlineEmpty, itemParams());

        setOpen(false);
    }

    public boolean isOpen() { return open; }

    public void setOpen(boolean value) {
        open = value;
        setVisibility(value ? View.VISIBLE : View.GONE);
        scrim.setVisibility(value ? View.VISIBLE : View.GONE);
        drawer.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    public void close() {
        if (!open) return;
        setOpen(false);
        onDismiss.run();
    }

    public void bind(String title, boolean bookmarked, boolean canBookmark, boolean canReadAsMarkdown,
                     int fontSize, boolean canDecreaseFont, boolean canIncreaseFont, int progress) {
        pageTitle.setText(title);
        bookmarkButton.setText(bookmarked ? R.string.bookmark_remove : R.string.bookmark_add);
        bookmarkButton.setEnabled(canBookmark);
        markdownButton.setEnabled(canReadAsMarkdown);
        decreaseFontButton.setEnabled(canDecreaseFont);
        increaseFontButton.setEnabled(canIncreaseFont);
        fontSizeValue.setText(getContext().getString(R.string.font_size_value, fontSize));
        int value = Math.max(0, Math.min(100, progress));
        updateProgress(value);
    }

    public void updateProgress(int progress) {
        int value = Math.max(0, Math.min(100, progress));
        progressBar.setProgress(value);
        progressLabel.setText(getContext().getString(R.string.reading_progress, value));
    }

    public void showOutline(List<OutlineItem> items, String emptyMessage) {
        outlineRows.removeAllViews();
        for (OutlineItem item : items) {
            TextView row = label(item.title, 14);
            row.setMaxLines(2);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setPadding(dp(12 + (Math.max(1, Math.min(6, item.level)) - 1) * 14), 0, dp(8), 0);
            row.setMinHeight(dp(44));
            row.setOnClickListener(view -> {
                actions.onNavigateToHeading(item.index);
                close();
            });
            outlineRows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        outlineEmpty.setText(items.isEmpty() ? emptyMessage : "");
        outlineEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private LinearLayout.LayoutParams itemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        return params;
    }

    private TextView label(String text, int size) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(textColor());
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private Button panelButton(String label, Runnable action) {
        return panelButton(label, label, action);
    }

    private Button panelButton(String label, String description, Runnable action) {
        Button button = button(label, description, action);
        button.setTextSize(14);
        button.setMinWidth(0);
        return button;
    }

    private Button button(String label, String description, Runnable action) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(7), 0, dp(7), 0);
        button.setContentDescription(description);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int textColor() { return nightMode ? 0xffe5e9e3 : 0xff252923; }
    private int mutedColor() { return nightMode ? 0xffa2aaa1 : 0xff657067; }
}
