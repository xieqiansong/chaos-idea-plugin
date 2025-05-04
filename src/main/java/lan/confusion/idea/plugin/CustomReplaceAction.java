package lan.confusion.idea.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import lan.confusion.common.text.CodingUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * 自定义替换选中文本的Action
 */
public class CustomReplaceAction {

    private static void replace(AnActionEvent anActionEvent, Function<String, String> replaceFunc) {
        final Editor editor = anActionEvent.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        final Document document = editor.getDocument();
        final SelectionModel selectionModel = editor.getSelectionModel();
        final Project project = anActionEvent.getProject();

        // 处理选中文本的情况
        if (selectionModel.hasSelection()) {
            int start = selectionModel.getSelectionStart();
            int end = selectionModel.getSelectionEnd();
            String selectedText = selectionModel.getSelectedText();
            if (selectedText == null) return;

            String converted = replaceFunc.apply(selectedText);
            WriteCommandAction.runWriteCommandAction(project, () -> document.replaceString(start, end, converted));
        }
        // 处理光标所在单词的情况
        else {
            Caret caret = editor.getCaretModel().getCurrentCaret();
            int offset = caret.getOffset();

            // 获取光标所在单词的边界
            CharSequence text = document.getCharsSequence();
            int start = offset;
            int end = offset;

            // 查找单词左边界
            while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) start--;
            // 查找单词右边界
            while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) end++;

            TextRange wordRange = start < end ? new TextRange(start, end) : null;
            if (wordRange == null) return;

            String word = document.getText(wordRange);
            String converted = replaceFunc.apply(word);
            WriteCommandAction.runWriteCommandAction(project, () -> document.replaceString(wordRange.getStartOffset(), wordRange.getEndOffset(), converted));
        }
    }

    public static class Reverse extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, CodingUtils::reverse);
        }
    }

    public static class Minify extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, CodingUtils::minify);
        }
    }

    public static class LinesToSqlWhereIn extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, CodingUtils::linesToSqlWhereIn);
        }
    }

    public static class CsvToSqlValues extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, CodingUtils::csvToSqlValues);
        }
    }

    public static class SwitchFileSeparator extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, CodingUtils::switchFileSeparator);
        }
    }

}
