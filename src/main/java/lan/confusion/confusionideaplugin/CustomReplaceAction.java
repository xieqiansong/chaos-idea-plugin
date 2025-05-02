package lan.confusion.confusionideaplugin;

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
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
            handleSelection(document, selectionModel, project, replaceFunc);
        }
        // 处理光标所在单词的情况
        else {
            handleWordAtCaret(editor, document, project, replaceFunc);
        }
    }


    public static void handleSelection(Document document, SelectionModel selectionModel, Project project, Function<String, String> replaceFunc) {
        int start = selectionModel.getSelectionStart();
        int end = selectionModel.getSelectionEnd();
        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null) return;

        String converted = replaceFunc.apply(selectedText);
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(start, end, converted);
        });
    }

    public static void handleWordAtCaret(Editor editor, Document document, Project project, Function<String, String> replaceFunc) {
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
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(wordRange.getStartOffset(), wordRange.getEndOffset(), converted);
        });
    }

    public static class Reverse extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, text -> new StringBuilder(text).reverse().toString());
        }
    }

    public static class Minify extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, text -> {
                // 处理换行符：将除了最后一个换行符外的所有换行符替换为空格
                Pattern newlinePattern = Pattern.compile("\\n(?=[^\\n]*\\n)");
                Matcher newlineMatcher = newlinePattern.matcher(text);
                text = newlineMatcher.replaceAll(" ");
                // 处理空格：替换不在引号内的多个空格为一个空格（注意潜在的限制）
                text = text.replaceAll("([^'\"\\s]+)\\s+", "$1 ");
                return text;
            });
        }
    }

    public static class LinesToSqlWhereIn extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, text -> {
                // 移除首尾空白并分割为行数组
                String[] items = text.trim().split("\\r?\\n");
                // 使用流过滤空行并修剪空白
                String filtered = Arrays.stream(items)
                        .map(String::trim)
                        .filter(item -> !item.isEmpty())
                        .collect(Collectors.joining("', '"));
                // 处理空结果情况
                if (filtered.isEmpty()) return "('')";
                return String.format("('%s')", filtered);
            });
        }
    }


    public static class CsvToSqlValues extends AnAction {
        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            replace(anActionEvent, text -> {
                // 1. 移除首尾空白并分割为行数组
                String[] rows = text.trim().split("\n");

                // 2. 处理每一行并生成SQL VALUES子句
                return Arrays.stream(rows)
                        .map(row -> {
                            // 2.1 按逗号分割列值
                            String[] columns = row.split(",");

                            // 2.2 处理每个列值：修剪空格、转义单引号、包裹单引号
                            String processedColumns = Arrays.stream(columns)
                                    .map(column -> {
                                        String trimmed = column.trim();
                                        String escaped = trimmed.replace("'", "''");
                                        return "'" + escaped + "'";
                                    })
                                    .collect(Collectors.joining(", ")); // 列值用逗号+空格连接

                            // 2.3 包裹整行为括号格式
                            return "(" + processedColumns + ")";
                        })
                        // 3. 过滤空行（尽管理论上不会存在，但保留原逻辑）
                        .filter(processedRow -> !processedRow.trim().isEmpty())
                        // 4. 用逗号和换行连接所有行
                        .collect(Collectors.joining(",\n"));
            });
        }
    }

}
