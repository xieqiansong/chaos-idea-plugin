package lan.confusion.idea.plugin;

import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.group.RegisterGroup;
import com.maddyhome.idea.vim.register.Register;

import javax.swing.*;
import java.awt.*;

public class MyToolWindow {
    private JPanel myToolWindowContent;
    private Timer refreshTimer;

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
            myToolWindowContent.add(new JLabel("IdeaVim dependency not found"), BorderLayout.CENTER);
        }
    }

    private void displayRegisters(RegisterGroup registerGroup) {
        myToolWindowContent.removeAll(); // 清除旧内容
        JPanel registersPanel = new JPanel();
        registersPanel.setLayout(new BoxLayout(registersPanel, BoxLayout.Y_AXIS));

        for (Register register : registerGroup.getRegisters()) {
            if (register != null) {
                String text = register.getText().length() > 50 ?
                        register.getText().substring(0, 50) + "..." :
                        register.getText();

                JLabel label = new JLabel("\"" + register.getName() + "   " + text);
                registersPanel.add(label);
            }
        }

        JScrollPane scrollPane = new JScrollPane(registersPanel);
        myToolWindowContent.add(scrollPane, BorderLayout.CENTER);
        myToolWindowContent.revalidate();
        myToolWindowContent.repaint();
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer(1000, e -> {
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