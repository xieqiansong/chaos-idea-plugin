package lan.confusion.idea.plugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 编码工具类 */
public class CodingUtils {
    private CodingUtils() {}

    /** 反转字符串 */
    public static String reverse(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    /** 压缩字符串 */
    public static String minify(String text) {
        // 处理换行符：将除了最后一个换行符外的所有换行符替换为空格
        Pattern newlinePattern = Pattern.compile("\\n(?=[^\\n]*\\n)");
        Matcher newlineMatcher = newlinePattern.matcher(text);
        text = newlineMatcher.replaceAll(" ");
        // 处理空格：替换不在引号内的多个空格为一个空格（注意潜在的限制）
        text = text.replaceAll("([^'\"\\s]+)\\s+", "$1 ");
        return text;
    }

    /** 将文本中的每行转换为SQL的WHERE IN (...)子句 */
    public static String linesToSqlWhereIn(String text) {
        // 移除首尾空白并分割为行数组
        String[] items = text.trim().split("\\r?\\n");
        // 使用流过滤空行并修剪空白
        String filtered =
                Arrays.stream(items)
                        .map(String::trim)
                        .filter(item -> !item.isEmpty())
                        .collect(Collectors.joining("', '"));
        // 处理空结果情况
        if (filtered.isEmpty()) return "('')";
        return String.format("('%s')", filtered);
    }

    /** 将CSV文本转换为SQL VALUES子句 */
    public static String csvToSqlValues(String text) {
        // 1. 移除首尾空白并分割为行数组
        String[] rows = text.trim().split("\n");
        // 2. 处理每一行并生成SQL VALUES子句
        return Arrays.stream(rows)
                .map(
                        row -> {
                            // 2.1 按逗号分割列值
                            String[] columns = row.split(",");
                            // 2.2 处理每个列值：修剪空格、转义单引号、包裹单引号
                            String processedColumns =
                                    Arrays.stream(columns)
                                            .map(
                                                    column -> {
                                                        String trimmed = column.trim();
                                                        String escaped = trimmed.replace("'", "''");
                                                        return "'" + escaped + "'";
                                                    })
                                            .collect(Collectors.joining(", ")); // 列值用逗号+空格连接
                            // 2.3 包裹整行为括号格式
                            return "(" + processedColumns + ")";
                        })
                // 3. 过滤空行（尽管理论上不会存在，但保留原逻辑）
                .filter(processedRow -> !processedRow.trim().isEmpty())
                // 4. 用逗号和换行连接所有行
                .collect(Collectors.joining(",\n"));
    }

    /** 切换文件分隔符 */
    public static String switchFileSeparator(String text) {
        // 定义正则切换顺序
        String BACKSLASH = "/", SLASH = "\\\\", DOUBLE_SLASH = "\\\\\\\\";
        Map<String, String> nextReplacementMap =
                new HashMap<>() {
                    {
                        put(BACKSLASH, SLASH);
                        put(SLASH, DOUBLE_SLASH);
                        put(DOUBLE_SLASH, BACKSLASH);
                    }
                };
        Function<String, String> currentReplacement =
                (input) -> {
                    for (int i = 0; i < input.length(); i++) {
                        if (input.charAt(i) == '/') return BACKSLASH;
                        if (input.charAt(i) == '\\') {
                            // 检查下一个字符是否也是反斜杠
                            if (i + 1 < input.length() && input.charAt(i + 1) == '\\')
                                return DOUBLE_SLASH;
                            return SLASH;
                        }
                    }
                    return BACKSLASH;
                };
        // 替换所有连续的正斜杠或反斜杠为新的分隔符
        return text.replaceAll("[/\\\\]+", nextReplacementMap.get(currentReplacement.apply(text)));
    }
}
