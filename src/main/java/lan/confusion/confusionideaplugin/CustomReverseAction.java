package lan.confusion.confusionideaplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;

public class CustomReverseAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        // 替换逻辑
        String replacedText = new StringBuilder(selectedText).reverse().toString();

        // 关键修改：使用 WriteCommandAction 包裹文档操作
        WriteCommandAction.runWriteCommandAction(e.getProject(), () -> {
            Document document = editor.getDocument();
            int start = editor.getSelectionModel().getSelectionStart();
            int end = editor.getSelectionModel().getSelectionEnd();
            document.replaceString(start, end, replacedText);
        });
    }
}
