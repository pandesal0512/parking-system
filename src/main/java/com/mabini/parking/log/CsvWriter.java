package com.mabini.parking.log;

import java.util.List;

/**
 * Builds a CSV file that Excel, Google Sheets and LibreOffice all open correctly.
 * - every cell is quoted, so commas and line breaks inside text are safe
 * - a cell that starts with = + - @ is prefixed with an apostrophe, so a spreadsheet can never run it as a formula
 */
final class CsvWriter {

    private final StringBuilder out = new StringBuilder();

    CsvWriter row(List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.append(',');
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    private static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** UTF-8 with a byte order mark: Excel then shows names like "Dela Peña" correctly. */
    byte[] toBytes() {
        byte[] body = out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        return all;
    }
}
