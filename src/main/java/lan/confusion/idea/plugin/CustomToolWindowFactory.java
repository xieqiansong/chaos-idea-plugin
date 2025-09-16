package lan.confusion.idea.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class CustomToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 创建自定义面板
        CustomToolWindow customToolWindow = new CustomToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(customToolWindow.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }

}
