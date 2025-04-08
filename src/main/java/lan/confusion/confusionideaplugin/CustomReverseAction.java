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

/**
 *
 */
public class CustomReverseAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        final Document document = editor.getDocument();
        final SelectionModel selectionModel = editor.getSelectionModel();
        final Project project = e.getProject();

        // 处理选中文本的情况
        if (selectionModel.hasSelection()) {
            handleSelection(editor, document, selectionModel, project);
        }
        // 处理光标所在单词的情况
        else {
            handleWordAtCaret(editor, document, project);
        }
    }

    private void handleSelection(Editor editor, Document document, SelectionModel selectionModel, Project project) {
        int start = selectionModel.getSelectionStart();
        int end = selectionModel.getSelectionEnd();
        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null) return;

        String converted = doReplace(selectedText);
        replaceText(document, project, start, end, converted);
    }

    private void handleWordAtCaret(Editor editor, Document document, Project project) {
        Caret caret = editor.getCaretModel().getCurrentCaret();
        int offset = caret.getOffset();

        // 获取光标所在单词的边界
        TextRange wordRange = findWordRange(document, offset);
        if (wordRange == null) return;

        String word = document.getText(wordRange);
        String converted = doReplace(word);
        replaceText(document, project, wordRange.getStartOffset(), wordRange.getEndOffset(), converted);
    }

    private TextRange findWordRange(Document document, int offset) {
        CharSequence text = document.getCharsSequence();
        int start = offset;
        int end = offset;

        // 查找单词左边界
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }

        // 查找单词右边界
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
            end++;
        }

        return start < end ? new TextRange(start, end) : null;
    }


    private String doReplace(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    private void replaceText(Document document, Project project, int start, int end, String newText) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.replaceString(start, end, newText);
        });
    }
}
