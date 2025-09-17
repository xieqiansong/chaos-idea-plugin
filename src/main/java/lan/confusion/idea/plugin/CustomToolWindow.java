package lan.confusion.idea.plugin;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.register.Register;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

@Data
public class CustomToolWindow {
    private final JPanel content;
    private final Project project;

    public CustomToolWindow(Project project) {
        this.project = project;
        content = new JPanel(new BorderLayout());
        try {
            displayRegisters(this.project);
        } catch (NoClassDefFoundError e) {
            content.add(new JLabel("IdeaVim dependency not found"), BorderLayout.CENTER);
        }
        // 启动定时刷新
        Timer refreshTimer = new Timer(1000, actionEvent -> displayRegisters(this.project));
        refreshTimer.start();
    }

    private void displayRegisters(Project project) {
        if (!VimPlugin.isEnabled()) {
            return;
        }
        /* Vim Registers */
        java.util.List<RegisterDesc> list = VimPlugin.getRegister().getRegisters()
                .stream()
                .filter(Objects::nonNull)
                .filter(reg -> StrUtil.isNotBlank(reg.getText()))
                .map(o -> new RegisterDesc(o.getName(), o.getText()))
                .toList();

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (RegisterDesc reg : list) {
            String regText = reg.getText();
            String[] lines = regText.split("\n");
            StringBuilder cleanedText = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) cleanedText.append("⏎");
                cleanedText.append(lines[i].trim());
            }
//            int panelWidth = content.getWidth() > 0 ? content.getWidth() - 20 : 300;
//            panel.add(new JLabel(StrUtil.format(
//                    "<html><div style='width: {}px;'>\"<span style='color: red;'>{}</span>  {}</div></html>",
//                    panelWidth, reg.getName(), cleanedText.toString())));
            panel.add(new JLabel(StrUtil.format(
                    "<html><div style='width: 100%;'>\"<span style='color: red;'>{}</span>  {}</div></html>",
                    reg.getName(), cleanedText.toString())));
        }
        panel.add(new JSeparator());

        JScrollPane scrollPane = new JScrollPane(panel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        content.removeAll();
        content.add(scrollPane, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    @Data
    @AllArgsConstructor
    public static class RegisterDesc {
        private Character name;
        private String text;
    }

}
