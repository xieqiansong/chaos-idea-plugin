package lan.confusion.idea.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.jetbrains.annotations.NotNull;

public class SendToTerminalAction extends AnAction {

    private static final Logger LOG = Logger.getInstance(SendToTerminalAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        Project project = e.getProject();
        if (project == null) return;

        // 获取Terminal工具窗口
        ToolWindow terminalWindow =
                ToolWindowManager.getInstance(project).getToolWindow("Terminal");
        if (terminalWindow == null) return;

        // 获取当前活动的编辑器（用于后续焦点恢复）
        Editor activeEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();

        // 激活Terminal窗口
        terminalWindow.activate(
                () -> {
                    // 优先使用 Reworked 终端 API（IDEA 2025.2+ 的默认终端引擎），失败则回退旧版 API
                    boolean sent =
                            sendToReworkedTerminal(project, terminalWindow, selectedText)
                                    || sendToNewTerminal(terminalWindow, selectedText)
                                    || sendToClassicTerminal(terminalWindow, selectedText);
                    if (!sent) {
                        LOG.warn("Failed to send text to terminal synchronously, will retry if needed");
                        // Reworked 终端的 tab 可能是异步创建的，安排后台重试
                        scheduleReworkedSend(project, terminalWindow, selectedText);
                    }

                    // 主动将焦点切回编辑器
                    if (activeEditor != null) {
                        JComponent editorComponent = activeEditor.getContentComponent();
                        SwingUtilities.invokeLater(editorComponent::requestFocusInWindow);
                    }
                });
    }

    /**
     * Reworked 终端 API（IDEA 2025.2 起为默认终端引擎，IDEA 2025.3 起提供公开的实验性 API）。
     *
     * <p>Reworked 终端的组件树中不再包含 JBTerminalWidget，需要通过
     * TerminalToolWindowTabsManager 获取已打开的终端 tab，再通过 TerminalView 发送命令。
     * 这里全部使用反射调用，避免依赖新版 SDK 导致插件无法在旧版本编译。
     */
    private static boolean sendToReworkedTerminal(
            Project project, ToolWindow terminalWindow, String command) {
        try {
            Class<?> managerClass =
                    Class.forName("com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager");
            Object manager =
                    managerClass.getMethod("getInstance", Project.class).invoke(null, project);

            Class<?> tabClass =
                    Class.forName("com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab");
            Method getTabs = managerClass.getMethod("getTabs");
            List<?> tabs = (List<?>) getTabs.invoke(manager);

            // 优先向当前选中的终端 tab 发送，否则使用第一个 tab
            Content selected =
                    terminalWindow == null
                            ? null
                            : terminalWindow.getContentManager().getSelectedContent();
            if (tabs != null && !tabs.isEmpty()) {
                Method getContent = tabClass.getMethod("getContent");
                Object targetTab = null;
                for (Object tab : tabs) {
                    if (selected != null && selected.equals(getContent.invoke(tab))) {
                        targetTab = tab;
                        break;
                    }
                }
                if (targetTab == null) {
                    targetTab = tabs.get(0);
                }
                Object view = tabClass.getMethod("getView").invoke(targetTab);
                if (view != null) {
                    sendText(view, command);
                    LOG.debug("Sent command via reworked terminal tab");
                    return true;
                }
            }

            // tab 尚未就绪时，退而直接在选中内容的组件树中查找 TerminalView
            if (selected != null && sendViaTerminalViewComponent(selected.getComponent(), command)) {
                LOG.debug("Sent command via reworked terminal view component");
                return true;
            }
        } catch (Throwable t) {
            LOG.debug("Reworked terminal API is not available, fall back to other APIs", t);
        }
        return false;
    }

    /**
     * 通过反射调用 TerminalView.createSendTextBuilder().shouldExecute().send(command)，
     * 相当于在终端中粘贴文本并按回车执行。
     */
    private static void sendText(Object view, String command) throws ReflectiveOperationException {
        Object builder = view.getClass().getMethod("createSendTextBuilder").invoke(view);
        builder = builder.getClass().getMethod("shouldExecute").invoke(builder);
        builder.getClass().getMethod("send", String.class).invoke(builder, command);
    }

    /**
     * Reworked 终端的 TerminalView 是内容组件树中的一员，递归查找并发送。
     */
    private static boolean sendViaTerminalViewComponent(Component root, String command) {
        try {
            Class<?> viewClass = Class.forName("com.intellij.terminal.frontend.view.TerminalView");
            return findAndSendTerminalView(root, viewClass, command);
        } catch (Throwable t) {
            LOG.debug("TerminalView component search failed", t);
            return false;
        }
    }

    private static boolean findAndSendTerminalView(Component component, Class<?> viewClass, String command) {
        if (component == null) {
            return false;
        }
        try {
            if (viewClass.isInstance(component)) {
                sendText(component, command);
                return true;
            }
        } catch (Throwable t) {
            LOG.debug("Failed to send via TerminalView component", t);
            return false;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (findAndSendTerminalView(child, viewClass, command)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reworked 终端的 tab 是异步创建的，若同步发送失败（终端刚被激活时 tab 尚未就绪），
     * 则在 EDT 上后台重试几次，直到成功或超时放弃。
     */
    private static void scheduleReworkedSend(
            Project project, ToolWindow terminalWindow, String command) {
        try {
            // 仅在当前 IDE 存在 Reworked 终端 API 时才安排重试
            Class.forName("com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager");
        } catch (ClassNotFoundException e) {
            return;
        }
        final int[] attempts = {0};
        Timer timer = new Timer(200, null);
        timer.addActionListener(
                ev -> {
                    if (sendToReworkedTerminal(project, terminalWindow, command)) {
                        timer.stop();
                        return;
                    }
                    if (++attempts[0] >= 10) {
                        timer.stop();
                    }
                });
        timer.start();
    }

    /**
     * 新版终端 API（IDEA 2025.1+ 引入的 Reworked 终端，2025.2 起为默认引擎）。
     *
     * <p>IDEA 2025 起 JBTerminalWidget 仅用于 Classic 终端，Reworked 终端的组件树中不再包含
     * JBTerminalWidget，需要通过 TerminalToolWindowManager.findWidgetByContent(Content) 获取
     * 统一抽象的 TerminalWidget，再调用 sendCommandToExecute(String) 发送命令。
     * 这里全部使用反射调用，避免依赖新版 SDK 导致插件无法在旧版本编译。
     */
    private static boolean sendToNewTerminal(ToolWindow terminalWindow, String command) {
        try {
            Class<?> managerClass =
                    Class.forName("org.jetbrains.plugins.terminal.TerminalToolWindowManager");
            Method findWidgetMethod = managerClass.getMethod("findWidgetByContent", Content.class);

            ContentManager contentManager = terminalWindow.getContentManager();
            Content selected = contentManager.getSelectedContent();
            if (selected != null && invokeSendCommand(findWidgetMethod, selected, command)) {
                return true;
            }
            for (Content content : contentManager.getContents()) {
                if (content != selected && invokeSendCommand(findWidgetMethod, content, command)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            LOG.debug("New terminal API is not available, fall back to classic API", t);
        }
        return false;
    }

    private static boolean invokeSendCommand(Method findWidgetMethod, Content content, String command) {
        try {
            Object widget = findWidgetMethod.invoke(null, content);
            if (widget == null) {
                return false;
            }
            Method sendCommandMethod =
                    widget.getClass().getMethod("sendCommandToExecute", String.class);
            sendCommandMethod.invoke(widget, command);
            return true;
        } catch (Throwable t) {
            LOG.debug("Failed to send command via new terminal API", t);
            return false;
        }
    }

    /**
     * 旧版终端 API：递归查找 JBTerminalWidget（Classic 终端，2024.x 及更早版本）。
     */
    private static boolean sendToClassicTerminal(ToolWindow terminalWindow, String command) {
        if (!terminalWindow.isAvailable()) {
            return false;
        }
        Content content = terminalWindow.getContentManager().getContent(0);
        if (content == null) {
            return false;
        }

        JBTerminalWidget terminalWidget = findTerminalWidget(content.getComponent());
        if (terminalWidget == null) {
            return false;
        }

        TtyConnector connector = terminalWidget.getTtyConnector();
        if (!(connector instanceof ProcessTtyConnector)) {
            return false;
        }

        try {
            // 发送命令并执行（\r对应回车）
            connector.write(command + "\r");
            return true;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
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
