package com.factory.security.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 导出工具：RFC 4180 风格转义，统一使用 UTF-8 BOM，保证中文在 Excel 中正常显示
 */
public final class CsvExporter {

    /** 写入 CSV 文件开头的 UTF-8 BOM，Excel 打开时按 UTF-8 解码，避免中文乱码 */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvExporter() {
    }

    public static void write(OutputStream outputStream, String[] headers, List<String[]> rows) throws IOException {
        outputStream.write(UTF8_BOM);
        writeLine(outputStream, headers);
        for (String[] row : rows) {
            writeLine(outputStream, row);
        }
        outputStream.flush();
    }

    private static void writeLine(OutputStream outputStream, String[] cells) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(cells[i]));
        }
        line.append("\r\n");
        outputStream.write(line.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 逗号、双引号按 RFC 4180 规则用双引号包裹、内部双引号双写；
     * 换行、制表符等控制字符统一替换为空格（比引号内保留换行更稳妥，
     * 避免超长名称等异常数据让部分导入工具误判行边界）
     */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // 换行、制表符等控制字符会破坏 CSV 行结构，统一替换为空格
            if (c < 0x20) {
                cleaned.append(' ');
            } else {
                cleaned.append(c);
            }
        }
        String text = cleaned.toString();
        if (text.contains(",") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
