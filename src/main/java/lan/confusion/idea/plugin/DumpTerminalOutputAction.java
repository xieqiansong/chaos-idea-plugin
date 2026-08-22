package lan.confusion.idea.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** 导出终端内容到文件（$CUSTOM_HOME/terminal-out/），并确保定时循环已启动。 */
public final class DumpTerminalOutputAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        java.nio.file.Path dir = TerminalOutputDumper.resolvedOutputDir();
        if (dir == null) {
            TerminalOutputDumper.notifyError(project, "未找到环境变量 CUSTOM_HOME，无法导出终端内容");
            return;
        }
        // 确保后台定时导出已启动（项目打开时也可能已由启动钩子启动，幂等）
        TerminalOutputDumper.startAutoDump(project);
        // 立即导出一轮，满足手动触发语义
        TerminalOutputDumper.dumpAll(project);
        TerminalOutputDumper.notifyInfo(project, "已导出到：\n" + dir.toString().replace("\\", "/"));
    }
}