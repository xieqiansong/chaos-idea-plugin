package lan.confusion.idea.plugin;

import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.register.Register;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class MyToolWindow {
    private JPanel toolWindowContent;
    Project project;

    public JComponent getContent() {
        return toolWindowContent;
    }

    public MyToolWindow(Project project) {
        this.project = project;
        initializeContent();
        // 启动定时刷新
        Timer refreshTimer = new Timer(1000, actionEvent -> displayRegisters(this.project));
        refreshTimer.start();
    }

    private void initializeContent() {
        toolWindowContent = new JPanel(new BorderLayout());
        try {
            displayRegisters(project);
        } catch (NoClassDefFoundError e) {
            toolWindowContent.add(new JLabel("IdeaVim dependency not found"), BorderLayout.CENTER);
        }
    }

    private void displayRegisters(Project project) {
        if (!VimPlugin.isEnabled()) {
            return;
        }
        // 清除旧内容
        toolWindowContent.removeAll();
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        /* Vim Registers */
        panel.add(new JLabel("<html><span style='font-weight: bold; color: #FFC66D;'>Vim Registers</span></html>"));
        for (Register reg : VimPlugin.getRegister().getRegisters()) {
            if (Objects.nonNull(reg) && !reg.getText().isEmpty()) {
                String text = reg.getText();
                text = text.replace("\n", "⏎").replace("\r", "").trim();
                text = text.length() > 100 ? text.substring(0, 100) + "..." : text;
                String labelText = String.format("\"%s  %s", reg.getName(), text);
                panel.add(new JLabel(labelText));
            }
        }
        panel.add(new JSeparator());

        JScrollPane scrollPane = new JScrollPane(panel);
        toolWindowContent.add(scrollPane, BorderLayout.CENTER);
        toolWindowContent.revalidate();
        toolWindowContent.repaint();
    }

}
