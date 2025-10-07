package lan.confusion.idea.plugin;

import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.maddyhome.idea.vim.KeyHandler;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.api.ExecutionContext;
import com.maddyhome.idea.vim.api.VimEditor;
import com.maddyhome.idea.vim.api.VimInjectorKt;
import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.command.OperatorArguments;
import com.maddyhome.idea.vim.extension.ExtensionHandler;
import com.maddyhome.idea.vim.extension.VimExtension;
import com.maddyhome.idea.vim.helper.EngineStringHelper;
import com.maddyhome.idea.vim.key.KeyMapping;
import com.maddyhome.idea.vim.key.MappingInfo;
import com.maddyhome.idea.vim.newapi.IjVimEditorKt;
import com.maddyhome.idea.vim.register.Register;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author xqs
 */
public class Peekaboo implements VimExtension {
    // UI Colors
    private static final class Colors {
        static final String REGISTER = "#000000";
        static final String TEXT = "#000000";
        static final String HEADER = "#FFC66D";
        static final String COMMENT = "#808080";
    }

    @Override
    public @NotNull String getName() {
        return "peekaboo";
    }

    @Override
    public void init() {
        // Register quote (") for normal mode and Ctrl+R for insert mode
        registerKeyMapping(MappingMode.NORMAL, Collections.singletonList(KeyStroke.getKeyStroke('"')));
        registerKeyMapping(MappingMode.INSERT, Collections.singletonList(KeyStroke.getKeyStroke("control R")));
    }

    // Helper method to register key mappings for a specific mode
    private void registerKeyMapping(MappingMode mode, List<KeyStroke> keyStrokes) {
        KeyMapping modeKeyMapping = VimPlugin.getKey().getKeyMapping(mode);
        // Handle existing mappings
        List<Pair<List<KeyStroke>, MappingInfo>> mappings = modeKeyMapping.getMapTo(keyStrokes);
        for (Pair<List<KeyStroke>, MappingInfo> mapping : mappings) {
            putKeyMapping(mode, mapping.getSecond().getFromKeys(), keyStrokes);
        }
        // Add default mapping if none exists
        if (modeKeyMapping.get(keyStrokes) == null) {
            putKeyMapping(mode, keyStrokes, keyStrokes);
        }
    }

    private void putKeyMapping(MappingMode mode, List<KeyStroke> fromKeys, List<KeyStroke> originalKeys) {
        VimPlugin.getKey()
                .putKeyMapping(EnumSet.of(mode), fromKeys, getOwner(), new ShowRegistersHandler(originalKeys), false);
    }

    private record ShowRegistersHandler(List<KeyStroke> originalKeyStrokes) implements ExtensionHandler {
        @Override
        public void execute(@NotNull VimEditor vimEditor, @NotNull ExecutionContext context, @NotNull OperatorArguments operatorArguments) {
            // Re-execute the original key sequence
            KeyHandler keyHandler = KeyHandler.getInstance();
            for (KeyStroke keyStroke : originalKeyStrokes) {
                keyHandler.handleKey(vimEditor, keyStroke, context, false, false, keyHandler.getKeyHandlerState());
            }
            showPopup(vimEditor);
        }

        private void showPopup(VimEditor vimEditor) {
            StringBuilder html = new StringBuilder();
            html.append(StrUtil.format("""
                    <html>
                    <body style='margin: 3px; width: 100%%;  color: {};'>
                    <div style='font-family: monospace; min-width: 600px;'>
                    """, Colors.TEXT));
            html.append("<div style='margin-bottom: 15px;'>");
            html.append(String.format("<div style='margin-bottom: 8px; color: %s;'>%s</div>", Colors.HEADER, "Registers"));
            String[] SPECIAL_REGISTERS = {
                    "\"", "*", "+", "%", "#", ".", ":", "/", "=", "-",
                    "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
                    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            };
            for (String reg : SPECIAL_REGISTERS) {
                Optional.ofNullable(VimPlugin.getRegister().getRegister(reg.charAt(0)))
                        .ifPresent(r -> {
                            String content = getRegisterText(r);
                            if (StrUtil.isNotBlank(content)) {
                                appendRegister(html, reg, content);
                            }
                        });
            }
            html.append("</div>");
            html.append("</div></body></html>");
            showBalloon(vimEditor, html.toString());
        }

        // Helper method to show the balloon popup
        private void showBalloon(VimEditor vimEditor, String htmlContent) {
            // 使用透明背景色
            Color fillColor = new Color(255, 255, 255, 255);
            Balloon balloon = JBPopupFactory.getInstance()
                    .createHtmlTextBalloonBuilder(htmlContent, null, fillColor, null)
                    .setAnimationCycle(10)
                    .setHideOnClickOutside(true)
                    .setHideOnKeyOutside(true)
                    .createBalloon();
            Editor ijEditor = IjVimEditorKt.getIj(vimEditor);
            IdeFrame ideFrame = WindowManager.getInstance().getIdeFrame(ijEditor.getProject());
            if (Objects.nonNull(ideFrame)) {
                Point location = new Point(0, ideFrame.getComponent().getHeight() - 100);
                RelativePoint point = new RelativePoint(ideFrame.getComponent(), location);
                balloon.show(point, Balloon.Position.above);
            }
        }

        private String getRegisterText(Register register) {
            List<KeyStroke> text = VimInjectorKt.getInjector().getParser().parseKeys(register.getText());
            return EngineStringHelper.INSTANCE.toPrintableCharacters(text).substring(0, Math.min(text.size(), 200));
        }

        private void appendRegister(StringBuilder html, String register, String content) {
            html.append(String.format(
                    """
                            <div style='display: flex; margin-bottom: 4px; align-items: baseline;'>
                                <span style='color: %s; font-weight: bold;'>%s</span>
                                <span style='color: %s;'> → </span>
                                <span style='color: %s;'>%s</span>
                            </div>
                            """,
                    Colors.REGISTER,
                    register,
                    Colors.COMMENT,
                    Colors.TEXT,
                    formatRegisterContent(content)));
        }

        private String formatRegisterContent(String content) {
            if (StrUtil.isBlank(content)) {
                return StrUtil.EMPTY;
            }
            String formatted = content.replace("\n", "⏎").replace("<", "&lt;").replace(">", "&gt;");
            return formatted.length() > 70 ? formatted.substring(0, 70) + "..." : formatted;
        }
    }
}