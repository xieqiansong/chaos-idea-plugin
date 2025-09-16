package lan.confusion.idea.plugin;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.register.Register;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

public class CustomToolWindow {
    private final JPanel toolWindowContent;
    private final Project project;

    public JComponent getContent() {
        return toolWindowContent;
    }


    public CustomToolWindow(Project project) {
        this.project = project;
        toolWindowContent = new JPanel(new BorderLayout());
        try {
            displayRegisters(this.project);
        } catch (NoClassDefFoundError e) {
            toolWindowContent.add(new JLabel("IdeaVim dependency not found"), BorderLayout.CENTER);
        }
        // 启动定时刷新
        Timer refreshTimer = new Timer(1000, actionEvent -> displayRegisters(this.project));
        refreshTimer.start();
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
            if (Objects.nonNull(reg) && StrUtil.isNotBlank(reg.getText())) {
                String regText = reg.getText();
                // 去掉 regText 每行前后空格，制表符
                String[] lines = regText.split("\n");
                StringBuilder cleanedText = new StringBuilder();
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) cleanedText.append("⏎");
                    cleanedText.append(lines[i].trim());
                }
                String text = cleanedText.toString();
                panel.add(new JLabel(StrUtil.format("\"{}  {}", reg.getName(), text)));
            }
        }
        panel.add(new JSeparator());

        JScrollPane scrollPane = new JScrollPane(panel);
        toolWindowContent.add(scrollPane, BorderLayout.CENTER);
        toolWindowContent.revalidate();
        toolWindowContent.repaint();
    }

}
