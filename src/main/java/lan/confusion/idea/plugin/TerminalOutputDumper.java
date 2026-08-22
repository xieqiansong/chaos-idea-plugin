package lan.confusion.idea.plugin;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 将当前项目所有已打开的 Reworked 终端（IDEA 2025.2+ 默认终端引擎）的全部显示内容导出到文件。
 *
 * <p>导出路径：{@code $CUSTOM_HOME/terminal-out/<终端标题>.log}，每个终端一个文件。
 * 供手动「Dump Terminal Output to File」动作与定时导出调度器共用。全程反射调用
 * Reworked 终端 2025.3+ 的实验性 API，避免插件在旧版本 IDE 编译时引入新 SDK 依赖。
 */
public final class TerminalOutputDumper {

    private static final Logger LOG = Logger.getInstance(TerminalOutputDumper.class);

    public static final String ENV_HOME = "CUSTOM_HOME";
    public static final String OUT_DIR = "terminal-out";
    public static final String ENV_INTERVAL = "CUSTOM_TERMINAL_DUMP_INTERVAL";
    private static final long DEFAULT_INTERVAL_MS = 5000;

    private static final String TABS_MANAGER =
            "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager";
    private static final String TAB_CLASS =
            "com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTab";

    private static final NotificationGroup NOTIFY_GROUP =
            new NotificationGroup("CustomScript.TerminalDump", NotificationDisplayType.BALLOON, true);

    private TerminalOutputDumper() {}

    private static volatile Thread autoDumpThread;

    /**
     * 启动后台守护线程持续导出终端内容（默认每 5 秒一轮，可用 CUSTOM_TERMINAL_DUMP_INTERVAL 秒覆盖）。
     * 幂等：重复调用不会创建多个线程。可在项目打开时或手动动作中调用。
     */
    public static synchronized void startAutoDump(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        Thread thread = autoDumpThread;
        if (thread != null && thread.isAlive()) {
            return;
        }
        autoDumpThread = new Thread(() -> autoLoop(project), "TerminalOutputLoop");
        autoDumpThread.setDaemon(true);
        autoDumpThread.start();
        Path dir = resolvedOutputDir();
        notifyInfo(
                project,
                "自动导出已开启（每 " + intervalMs() / 1000 + " 秒刷新）\n"
                        + (dir == null ? "（未找到 CUSTOM_HOME）" : dir.toString().replace("\\", "/")));
        LOG.debug("Auto terminal dump loop started");
    }

    private static void autoLoop(Project project) {
        long interval = intervalMs();
        boolean errNotified = false;
        while (!project.isDisposed() && Thread.currentThread() == autoDumpThread) {
            try {
                dumpAll(project);
                errNotified = false;
            } catch (Throwable t) {
                LOG.error("Terminal dump loop failed", t);
                if (!errNotified) {
                    errNotified = true;
                    notifyError(
                            project,
                            "定时导出循环出错：" + t.getClass().getSimpleName() + " - " + t.getMessage());
                    LOG.error("Terminal dump loop failed", t);
                }
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static long intervalMs() {
        try {
            String value = System.getenv(ENV_INTERVAL);
            if (value != null && !value.isBlank()) {
                long seconds = Long.parseLong(value.trim());
                if (seconds > 0) {
                    return seconds * 1000L;
                }
            }
        } catch (NumberFormatException ignored) {
            // 环境变量非法时回退到默认间隔
        }
        return DEFAULT_INTERVAL_MS;
    }

    /** 单个终端的导出内容：标题 + regular/alternative 两个输出缓冲的快照。 */
    private static final class TabOutput {
        final String title;
        @Nullable final Object regularSnapshot;
        @Nullable final Object alternativeSnapshot;

        TabOutput(String title, @Nullable Object regular, @Nullable Object alternative) {
            this.title = title;
            this.regularSnapshot = regular;
            this.alternativeSnapshot = alternative;
        }
    }

    /**
     * 解析最终的导出目录（$CUSTOM_HOME/terminal-out，绝对路径）。环境变量缺失时返回 null。
     */
    public static Path resolvedOutputDir() {
        String home = locateCustomHome();
        if (home == null || home.isBlank()) {
            return null;
        }
        return Paths.get(home, OUT_DIR).toAbsolutePath().normalize();
    }

    // ---- CUSTOM_HOME 解析 ----
    // Windows 的 explorer/shell 会在登录时缓存环境变量，直接改注册表 + 重启应用可能仍读到旧值。
    // 这里改为优先从注册表读取当前配置，兜底使用进程环境变量，并按 TTL 缓存避免频繁调 reg.exe。
    private static final long HOME_REFRESH_MS = 30_000;
    private static final String HKCU_ENV = "HKCU\\Environment";
    private static final String HKLM_ENV =
            "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment";

    private static volatile long homeRefreshedAt;
    private static volatile String homeCache;

    static String locateCustomHome() {
        long now = System.currentTimeMillis();
        String cached = homeCache;
        if (cached != null && now - homeRefreshedAt < HOME_REFRESH_MS) {
            return cached;
        }
        String registry = registryCustomHome();
        String fallback = System.getenv(ENV_HOME);
        String home =
                (registry != null && !registry.isBlank())
                        ? registry
                        : fallback;
        homeCache = home;
        homeRefreshedAt = now;
        return home;
    }

    /** 优先取用户环境，其次取系统环境；都为空白则返回 null（含非 Windows/reg 不可用场景）。 */
    private static String registryCustomHome() {
        String user = regQuery(ENV_HOME, HKCU_ENV);
        if (user != null && !user.isBlank()) {
            return user;
        }
        String machine = regQuery(ENV_HOME, HKLM_ENV);
        return (machine != null && !machine.isBlank()) ? machine : null;
    }

    /** 用 reg.exe 查询指定注册表项的值，失败或不存在返回 null。 */
    private static String regQuery(String valueName, String key) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return null;
        }
        try {
            Process process =
                    new ProcessBuilder("reg", "query", key, "/v", valueName)
                            .redirectErrorStream(true)
                            .start();
            String output;
            try (java.io.InputStream in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            process.waitFor();
            for (String line : output.split("\\R")) {
                int regIdx = line.indexOf("REG_");
                if (regIdx >= 0) {
                    // 取 REG_<类型> 之后的内容作为值
                    String value =
                            line.substring(regIdx)
                                    .replaceFirst("^REG_[A-Z0-9_]+\\s+", "")
                                    .trim();
                    return value;
                }
            }
        } catch (Throwable t) {
            LOG.debug("reg query failed for " + key, t);
        }
        return null;
    }

    /**
     * 异步导出当前项目所有已打开终端的内容。在后台线程收集快照并写文件，不阻塞调用线程。
     */
    public static void dumpAll(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        Path dir = resolvedOutputDir();
        if (dir == null) {
            notifyError(project, "未找到环境变量 " + ENV_HOME + "，无法导出终端内容");
            return;
        }

        ApplicationManager.getApplication()
                .executeOnPooledThread(
                        () -> {
                            try {
                                if (project.isDisposed()) {
                                    return;
                                }
                                List<TabOutput> outputs = collectTerminalOutputs(project);
                                if (outputs.isEmpty()) {
                                    LOG.debug("No terminal tabs to dump, skip");
                                    return;
                                }
                                writeFiles(dir, outputs);
                            } catch (Throwable t) {
                                LOG.error("Failed to dump terminal outputs", t);
                            }
                        });
    }

    /** 在 EDT 上收集快照（快照可安全地在后台线程读取文本）。 */
    private static List<TabOutput> collectTerminalOutputs(Project project) {
        AtomicReference<List<TabOutput>> holder = new AtomicReference<>(List.of());
        try {
            ApplicationManager.getApplication()
                    .invokeAndWait(() -> holder.set(doCollect(project)));
        } catch (Throwable t) {
            LOG.debug("Collect terminal outputs failed. Reworked terminal API may be unavailable", t);
        }
        return holder.get();
    }

    private static List<TabOutput> doCollect(Project project) {
        List<TabOutput> result = new ArrayList<>();
        try {
            Class<?> managerClass = Class.forName(TABS_MANAGER);
            Object manager = managerClass.getMethod("getInstance", Project.class).invoke(null, project);
            Object tabsObj = managerClass.getMethod("getTabs").invoke(manager);
            if (!(tabsObj instanceof List)) {
                return result;
            }
            List<?> tabs = (List<?>) tabsObj;

            Class<?> tabClass = Class.forName(TAB_CLASS);
            Method getContent = tabClass.getMethod("getContent");
            Method getView = tabClass.getMethod("getView");

            Set<String> usedTitles = new HashSet<>();
            for (Object tab : tabs) {
                String title = resolveTitle(getContent.invoke(tab), usedTitles);

                Object view = getView.invoke(tab);
                if (view == null) {
                    continue;
                }
                Object outputs = view.getClass().getMethod("getOutputModels").invoke(view);
                Object regularSnapshot = snapshotModel(outputs, "getRegular");
                Object alternativeSnapshot = snapshotModel(outputs, "getAlternative");
                result.add(new TabOutput(title, regularSnapshot, alternativeSnapshot));
            }
        } catch (Throwable t) {
            LOG.debug("Collect terminal outputs failed. Reworked terminal API may be unavailable", t);
        }
        return result;
    }

    private static String resolveTitle(Object content, Set<String> usedTitles) {
        String title = "Terminal";
        if (content instanceof Content) {
            String displayName = ((Content) content).getDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                title = displayName;
            }
        }
        // 多终端标题可能重复，追加序号保证文件不互相覆盖
        String base = title;
        int index = 1;
        while (!usedTitles.add(title)) {
            title = base + " (" + index++ + ")";
        }
        return title;
    }

    private static Object snapshotModel(Object outputs, String getter) {
        try {
            Object model = outputs.getClass().getMethod(getter).invoke(outputs);
            if (model == null) {
                return null;
            }
            return model.getClass().getMethod("takeSnapshot").invoke(model);
        } catch (Throwable t) {
            LOG.debug("Take output model snapshot failed: " + getter, t);
            return null;
        }
    }

    private static void writeFiles(Path dir, List<TabOutput> outputs) throws Exception {
        Files.createDirectories(dir);
        for (TabOutput output : outputs) {
            Path file = dir.resolve(sanitizeFileName(output.title) + ".log");
            String content = buildContent(output);
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String buildContent(TabOutput output) {
        StringBuilder sb = new StringBuilder();
        appendSnapshot(sb, output.regularSnapshot, false);
        appendSnapshot(sb, output.alternativeSnapshot, true);
        return sb.toString();
    }

    private static void appendSnapshot(StringBuilder sb, Object snapshot, boolean alternative) {
        if (snapshot == null) {
            return;
        }
        try {
            Object start = invokeByName(snapshot, "getStartOffset");
            Object end = invokeByName(snapshot, "getEndOffset");
            Object text = invokeByName(snapshot, "getText", start, end);
            String chunk = text == null ? "" : text.toString();
            if (chunk.isEmpty()) {
                return;
            }
            if (alternative) {
                sb.append("\n===== alternative buffer =====\n");
            }
            sb.append(chunk);
            if (!chunk.endsWith("\n")) {
                sb.append('\n');
            }
        } catch (Throwable t) {
            LOG.debug("Read output snapshot text failed", t);
        }
    }

    private static Object invokeByName(Object target, String methodName, Object... args) throws Exception {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
    }

    private static String sanitizeFileName(String name) {
        String safe =
                (name == null ? "Terminal" : name)
                        .replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_")
                        .trim();
        return safe.isBlank() ? "Terminal" : safe;
    }

    public static void notifyInfo(@Nullable Project project, String message) {
        Notifications.Bus.notify(NOTIFY_GROUP.createNotification(message, NotificationType.INFORMATION), project);
    }

    public static void notifyError(@Nullable Project project, String message) {
        Notifications.Bus.notify(NOTIFY_GROUP.createNotification(message, NotificationType.ERROR), project);
    }
}