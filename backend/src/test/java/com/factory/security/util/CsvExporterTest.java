package com.factory.security.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExporterTest {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private String export(String[] headers, java.util.List<String[]> rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.write(out, headers, rows);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void startsWithUtf8BomAndUsesCrlfLineEnding() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CsvExporter.write(out, new String[]{"列1"}, Collections.singletonList(new String[]{"值1"}));

        byte[] bytes = out.toByteArray();
        assertEquals((byte) UTF8_BOM[0], bytes[0]);
        assertEquals((byte) UTF8_BOM[1], bytes[1]);
        assertEquals((byte) UTF8_BOM[2], bytes[2]);
        assertTrue(out.toString(StandardCharsets.UTF_8).endsWith("\r\n"));
    }

    @Test
    void emptyRowsStillWritesHeaderLine() throws Exception {
        String csv = export(new String[]{"方案名称", "适用场景"}, Collections.emptyList());
        assertEquals("\uFEFF方案名称,适用场景\r\n", csv);
    }

    @Test
    void commasQuotesAndNewlinesAreEscapedPerRfc4180() throws Exception {
        String longName = "超长方案名称".repeat(40) + ",含逗号和\"引号\"";
        String csv = export(
                new String[]{"方案名称", "配件名称"},
                Collections.singletonList(new String[]{longName, "第一行\n第二行"}));

        String[] lines = csv.split("\r\n");
        // 数据内换行被替换为空格，不会产生额外 CSV 行
        assertEquals(2, lines.length);
        String expectedName = "\"" + longName.replace("\"", "\"\"") + "\"";
        assertTrue(lines[1].startsWith(expectedName + ","));
        assertTrue(lines[1].endsWith("第一行 第二行"));
    }

    @Test
    void nullAndChineseValuesAreHandled() throws Exception {
        String csv = export(
                new String[]{"方案名称", "规格单位"},
                Collections.singletonList(new String[]{null, "mm²"}));

        assertEquals("\uFEFF方案名称,规格单位\r\n,mm²\r\n", csv);
    }
}
