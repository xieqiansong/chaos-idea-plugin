package lan.confusion.idea.plugin;

import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.group.RegisterGroup;
import com.maddyhome.idea.vim.register.Register;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class MyToolWindow {
    private JPanel myToolWindowContent;
    private final RegisterConfig registerConfig = new RegisterConfig();

    // Register descriptions and configurations
    private static class RegisterConfig {
        final Map<String, String> DESCRIPTIONS =
                new HashMap<>() {
                    {
                        put("\"", "unnamed register: last deleted, changed, or yanked content");
                        put("-", "deleted or changed content smaller than one line");
                        put(":", "last executed command");
                        put(".", "last inserted text");
                        put("%", "name of the current file");
                        put("#", "name of the alternate file");
                        put("=", "expression register");
                        put("/", "last search pattern");
                        put("*", "system clipboard");
                        put("+", "system selection primary");
                    }
                };

        final String[] SPECIAL_REGISTERS = {"\"", "*", "+", "%", "#", ".", ":", "/", "=", "-"};
    }

    public MyToolWindow(Project project) {
        initializeContent();
        startRefreshTimer(); // 启动定时刷新
    }

    private void initializeContent() {
        myToolWindowContent = new JPanel(new BorderLayout());

        try {
            RegisterGroup registerGroup = VimPlugin.getRegister();
            displayRegisters(registerGroup);
        } catch (NoClassDefFoundError e) {
            myToolWindowContent.add(
                    new JLabel("IdeaVim dependency not found"), BorderLayout.CENTER);
        }
    }

    private void displayRegisters(RegisterGroup registerGroup) {
        myToolWindowContent.removeAll(); // 清除旧内容
        JPanel registersPanel = new JPanel();
        registersPanel.setLayout(new BoxLayout(registersPanel, BoxLayout.Y_AXIS));

        // 显示特殊寄存器
        addRegisterSection(
                registersPanel,
                "Special Registers",
                registerConfig.SPECIAL_REGISTERS,
                registerGroup);

        // 显示命名寄存器 (a-z)
        addNamedRegistersSection(registersPanel, registerGroup);

        // 显示最后yank的内容 (0)
        addLastYankSection(registersPanel, registerGroup);

        // 显示删除/更改历史 (1-9)
        addNumberedRegistersSection(registersPanel, registerGroup);

        JScrollPane scrollPane = new JScrollPane(registersPanel);
        myToolWindowContent.add(scrollPane, BorderLayout.CENTER);
        myToolWindowContent.revalidate();
        myToolWindowContent.repaint();
    }

    private void addRegisterSection(
            JPanel panel, String title, String[] registers, RegisterGroup registerGroup) {
        panel.add(
                new JLabel(
                        "<html><span style='font-weight: bold; color: #FFC66D;'>"
                                + title
                                + "</span></html>"));

        for (String reg : registers) {
            Register r = registerGroup.getRegister(reg.charAt(0));
            if (r != null && !r.getText().isEmpty()) {
                String description = registerConfig.DESCRIPTIONS.get(reg);
                String text = formatRegisterText(r.getText());
                String labelText =
                        String.format(
                                "\"%s  %s%s",
                                reg, text, description != null ? " (" + description + ")" : "");
                panel.add(new JLabel(labelText));
            }
        }
        panel.add(new JSeparator());
    }

    private void addNamedRegistersSection(JPanel panel, RegisterGroup registerGroup) {
        panel.add(
                new JLabel(
                        "<html><span style='font-weight: bold; color: #FFC66D;'>Named"
                                + " Registers</span></html>"));

        for (char c = 'a'; c <= 'z'; c++) {
            Register r = registerGroup.getRegister(c);
            if (r != null && !r.getText().isEmpty()) {
                String text = formatRegisterText(r.getText());
                panel.add(new JLabel("\"" + c + "  " + text));
            }
        }
        panel.add(new JSeparator());
    }

    private void addLastYankSection(JPanel panel, RegisterGroup registerGroup) {
        panel.add(
                new JLabel(
                        "<html><span style='font-weight: bold; color: #FFC66D;'>Last"
                                + " Yank</span></html>"));

        Register r = registerGroup.getRegister('0');
        if (r != null && !r.getText().isEmpty()) {
            String text = formatRegisterText(r.getText());
            panel.add(new JLabel("\"0  " + text));
        }
        panel.add(new JSeparator());
    }

    private void addNumberedRegistersSection(JPanel panel, RegisterGroup registerGroup) {
        panel.add(
                new JLabel(
                        "<html><span style='font-weight: bold; color: #FFC66D;'>Delete/Change"
                                + " History</span> <span style='color: #808080;'>(deleted/changed"
                                + " content larger than one line)</span></html>"));

        for (int i = 1; i <= 9; i++) {
            Register r = registerGroup.getRegister(String.valueOf(i).charAt(0));
            if (r != null && !r.getText().isEmpty()) {
                String text = formatRegisterText(r.getText());
                String description = (i == 1) ? " (most recent)" : "";
                panel.add(new JLabel("\"" + i + "  " + text + description));
            }
        }
        panel.add(new JSeparator());
    }

    private String formatRegisterText(String text) {
        if (text == null) return "";
        String formatted = text.replace("\n", "⏎").replace("\r", "");
        return formatted.length() > 100 ? formatted.substring(0, 100) + "..." : formatted;
    }

    private void startRefreshTimer() {
        // 忽略异常
        Timer refreshTimer =
                new Timer(
                        1000,
                        e -> {
                            try {
                                RegisterGroup registerGroup = VimPlugin.getRegister();
                                displayRegisters(registerGroup);
                            } catch (NoClassDefFoundError ex) {
                                // 忽略异常
                            }
                        });
        refreshTimer.start();
    }

    public JComponent getContent() {
        return myToolWindowContent;
    }
}
