package lan.confusion.idea.plugin;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.util.Disposer;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.command.CommandState;
import com.maddyhome.idea.vim.extension.VimExtension;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * QuickScope 本地重构实现（Java）。
 *
 * <p>功能想法参考 MIT 许可的 vim 插件 unblevable/quick-scope：
 * 高亮光标所在行中可通过 f / F / t / T 精确跳转到的目标字符（主/次两个候选）。</p>
 *
 * <p>本类为独立编写，不包含/搬运任何第三方代码。</p>
 *
 * @author xqs
 */
public class QuickScopeExtension implements VimExtension {
    private static final String PRIMARY_VAR = "qs_primary_color";
    private static final String SECONDARY_VAR = "qs_secondary_color";
    private static final String ACCEPTED_VAR = "qs_accepted_chars";
    private static final String DISABLE_DIFFS_VAR = "qs_disable_for_diffs";
    private static final String DEFAULT_ACCEPTED = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private char[] acceptedChars = DEFAULT_ACCEPTED.toCharArray();
    private boolean disableForDiffs = false;
    private boolean immediate = true; // 按 keep-it-simple 保留无变量时的默认立即高亮

    private Editor currentEditor;
    private final Set<RangeHighlighter> active = new LinkedHashSet<>();
    private CaretListener caretListener;
    private DisposableHolder disposable;

    private interface DisposableHolder {
        void dispose();
    }

    @Override
    public @NotNull String getName() {
        return "quickscope";
    }

    @Override
    public void init() {
        String primaryColor = readVarString(PRIMARY_VAR);
        String secondaryColor = readVarString(SECONDARY_VAR);
        String accepted = readVarString(ACCEPTED_VAR);
        if (accepted != null && !accepted.isEmpty()) {
            acceptedChars = accepted.toCharArray();
        }
        disableForDiffs = "1".equals(readVarString(DISABLE_DIFFS_VAR));

        planPrimaryColor = primaryColor;
        planSecondaryColor = secondaryColor;

        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
        caretListener = new QuickScopeCaretListener();
        // 用全局 disposable 承载监听器生命周期，dispose() 中再显式移除
        EditorFactory.getInstance().getEventMulticaster().addCaretListener(caretListener, Disposer.newDisposable());
        disposable = () -> multicaster.removeCaretListener(caretListener);
    }

    private String planPrimaryColor;
    private String planSecondaryColor;

    @Override
    public void dispose() {
        if (disposable != null) {
            disposable.dispose();
            disposable = null;
        }
        clearHighlights();
    }

    private String readVarString(String name) {
        try {
            Object v = VimPlugin.getVariableService().getGlobalVariableValue(name);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 高亮计算 ----

    private enum Direction {FORWARD, BACKWARD}

    private static class Target {
        final int position;
        final boolean primary;

        Target(int position, boolean primary) {
            this.position = position;
            this.primary = primary;
        }
    }

    private TextAttributes primaryAttributes(Editor editor) {
        java.awt.Color c = parseColor(planPrimaryColor)
                .orElseGet(() -> fallbackColor(editor, true));
        return new TextAttributes(c, null, c, EffectType.BOLD_LINE_UNDERSCORE, Font.BOLD);
    }

    private TextAttributes secondaryAttributes(Editor editor) {
        java.awt.Color c = parseColor(planSecondaryColor)
                .orElseGet(() -> fallbackColor(editor, false));
        return new TextAttributes(c, null, c, EffectType.LINE_UNDERSCORE, Font.PLAIN);
    }

    private java.util.Optional<java.awt.Color> parseColor(String hex) {
        try {
            if (hex == null) return java.util.Optional.empty();
            return java.util.Optional.ofNullable(java.awt.Color.decode(hex.trim()));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private java.awt.Color fallbackColor(Editor editor, boolean primary) {
        java.awt.Color base = editor.getColorsScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR) != null
                ? editor.getColorsScheme().getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR).getForegroundColor()
                : null;
        if (base == null) base = java.awt.Color.GRAY;
        if (primary) return base;
        java.awt.Color brighter = base.brighter();
        return brighter.equals(base) ? base.darker() : brighter;
    }

    private void highlightLine(Editor editor, Direction direction) {
        TextAttributes attrs = direction == Direction.FORWARD
                ? primaryAttributes(editor)
                : secondaryAttributes(editor);
        for (Target t : computeTargets(editor, direction)) {
            addRange(editor, t.position, t.position + 1, attrs);
        }
    }

    private void addRange(Editor editor, int start, int end, TextAttributes attrs) {
        RangeHighlighter h = editor.getMarkupModel().addRangeHighlighter(
                start, end, HighlighterLayer.SELECTION, attrs, HighlighterTargetArea.EXACT_RANGE);
        active.add(h);
    }

    /**
     * 计算当前行在光标附近可精确跳转的目标字符位置：
     * 依据"整行唯一性"筛选，向前/向后各取主、次两个候选。
     */
    private java.util.List<Target> computeTargets(Editor editor, Direction direction) {
        java.util.List<Target> result = new java.util.ArrayList<>(2);
        int lineStart = editor.getCaretModel().getPrimaryCaret().getVisualLineStart();
        int lineEnd = editor.getCaretModel().getPrimaryCaret().getVisualLineEnd();
        int caret = editor.getCaretModel().getPrimaryCaret().getOffset();
        if (lineEnd > editor.getDocument().getTextLength()) return result;

        CharSequence text = editor.getDocument().getCharsSequence();
        int[] lineCounts = countAcceptedOnLine(text, lineStart, lineEnd);

        int from;
        int to;
        int step;
        if (direction == Direction.FORWARD) {
            from = caret + 1;
            to = lineEnd;
            step = 1;
        } else {
            from = caret - 1;
            to = lineStart - 1; // 含 lineStart
            step = -1;
        }

        int primary = -1;
        int secondary = -1;
        for (int i = from; step > 0 ? i <= to : i >= to; i += step) {
            if (i < lineStart || i >= lineEnd) break;
            char c = text.charAt(i);
            if (!contains(acceptedChars, c)) continue;
            if (lineCounts[c] == 1) {
                if (primary == -1) primary = i;
                else if (secondary == -1) secondary = i;
            }
            if (primary != -1 && secondary != -1) break;
        }

        if (primary != -1) result.add(new Target(primary, true));
        if (secondary != -1) result.add(new Target(secondary, false));
        return result;
    }

    private int[] countAcceptedOnLine(CharSequence text, int start, int end) {
        int[] counts = new int[65536];
        for (int i = start; i < end && i < text.length(); i++) {
            char c = text.charAt(i);
            if (contains(acceptedChars, c)) counts[c]++;
        }
        return counts;
    }

    private boolean contains(char[] arr, char c) {
        for (char a : arr) if (a == c) return true;
        return false;
    }

    private void clearHighlights() {
        if (currentEditor == null) return;
        for (RangeHighlighter h : active) {
            if (h.isValid()) currentEditor.getMarkupModel().removeHighlighter(h);
        }
        active.clear();
    }

    private class QuickScopeCaretListener implements CaretListener {
        @Override
        public void caretPositionChanged(@NotNull CaretEvent e) {
            Editor editor = e.getEditor();
            if (currentEditor != editor) {
                clearHighlights();
                currentEditor = editor;
            }
            clearHighlights();

            CommandState.Mode mode = CommandState.getInstance(editor).getMode();
            if (mode == CommandState.Mode.INSERT) return;

            if (disableForDiffs && editor.getEditorKind() == EditorKind.DIFF) return;

            // 行内正向与反向各高亮候选
            boolean didForward = false;
            boolean didBackward = false;
            for (Target t : computeTargets(editor, Direction.FORWARD)) {
                addRange(editor, t.position, t.position + 1,
                        t.primary ? primaryAttributes(editor) : secondaryAttributes(editor));
                didForward = true;
            }
            for (Target t : computeTargets(editor, Direction.BACKWARD)) {
                addRange(editor, t.position, t.position + 1,
                        t.primary ? primaryAttributes(editor) : secondaryAttributes(editor));
                didBackward = true;
            }
        }
    }
}