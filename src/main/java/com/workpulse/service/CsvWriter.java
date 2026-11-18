package com.workpulse.service;

/** Minimal RFC-4180 CSV helper that also neutralises spreadsheet formula injection. */
public final class CsvWriter {

    private CsvWriter() { }

    /**
     * Quotes the value when needed. Values that start with = + - @ (or tab/CR) are prefixed with an
     * apostrophe so Excel/Sheets do not execute them as formulas (CSV injection).
     */
    public static String cell(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String row(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(values[i]));
        }
        return sb.append("\r\n").toString();
    }
}
