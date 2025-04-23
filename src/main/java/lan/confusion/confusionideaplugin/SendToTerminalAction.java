package lan.confusion.confusionideaplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class SendToTerminalAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;


        Project project = e.getProject();
        if (project == null) return;

        // 获取Terminal工具窗口
        ToolWindow terminalWindow = ToolWindowManager.getInstance(project).getToolWindow("Terminal");
        if (terminalWindow == null) return;

        // 获取当前活动的编辑器（用于后续焦点恢复）
        Editor activeEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();


        // 激活Terminal窗口
        terminalWindow.activate(() -> {
            // 使用新的方式获取 Terminal 组件
            JBTerminalWidget terminalWidget = null;
            if (terminalWindow.isAvailable()) {
                Content content = terminalWindow.getContentManager().getContent(0);
                if (content != null) {
                    JComponent component = content.getComponent();
                    terminalWidget = findTerminalWidget(component); // 递归查找组件
                }
            }

            assert terminalWidget != null;
            TtyConnector connector = terminalWidget.getTtyConnector();
            if (connector instanceof ProcessTtyConnector) {
                // 发送命令并执行（\r\n对应回车）
                String command = selectedText + "\r";
                try {
                    connector.write(command);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                // 主动将焦点切回编辑器
                if (activeEditor != null) {
                    JComponent editorComponent = activeEditor.getContentComponent();
                    SwingUtilities.invokeLater(editorComponent::requestFocusInWindow);
                }
            }
        });
    }

    // 辅助方法：递归查找 JBTerminalWidget 实例
    private static JBTerminalWidget findTerminalWidget(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JBTerminalWidget) {
                return (JBTerminalWidget) component;
            } else if (component instanceof Container) {
                JBTerminalWidget result = findTerminalWidget((Container) component);
                if (result != null) return result;
            }
        }
        return null;
    }
}
