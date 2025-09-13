package lan.confusion.idea.plugin;

import com.intellij.openapi.project.Project;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.group.RegisterGroup;
import com.maddyhome.idea.vim.register.Register;

import javax.swing.*;
import java.awt.*;

public class MyToolWindow {
    private JPanel myToolWindowContent;
    private Project project;

    public MyToolWindow(Project project) {
        this.project = project;
        initializeContent();
    }

    private void initializeContent() {
        myToolWindowContent = new JPanel(new BorderLayout());

        try {
            // 检查IdeaVim插件是否可用
            RegisterGroup registerGroup = VimPlugin.getRegister();
            if (registerGroup != null) {
                displayRegisters(registerGroup);
            } else {
                myToolWindowContent.add(new JLabel("IdeaVim plugin not available"), BorderLayout.CENTER);
            }
        } catch (NoClassDefFoundError e) {
            myToolWindowContent.add(new JLabel("IdeaVim dependency not found"), BorderLayout.CENTER);
        }
    }

    private void displayRegisters(RegisterGroup registerGroup) {
        JPanel registersPanel = new JPanel();
        registersPanel.setLayout(new BoxLayout(registersPanel, BoxLayout.Y_AXIS));

        // 获取所有寄存器
        for (Register register : registerGroup.getRegisters()) {

            if (register != null && register.getText() != null) {
                String text = register.getText().length() > 50 ?
                        register.getText().substring(0, 50) + "..." :
                        register.getText();

                JLabel label = new JLabel("\"" + register.getName() + "   " + text);
                registersPanel.add(label);
            }
        }

        JScrollPane scrollPane = new JScrollPane(registersPanel);
        myToolWindowContent.add(scrollPane, BorderLayout.CENTER);
    }

    public JComponent getContent() {
        return myToolWindowContent;
    }
}
