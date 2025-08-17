package com.bank.Bank_ChatBot.util;

import java.util.*;
import java.util.stream.Collectors;

public class TextTable {

    public static String table(List<String> headers, List<List<String>> rows) {
        if (headers == null) headers = List.of();
        if (rows == null) rows = List.of();

        int cols = Math.max(headers.size(), rows.stream().mapToInt(List::size).max().orElse(0));
        int[] widths = new int[cols];

        // compute widths
        for (int c = 0; c < cols; c++) {
            int w = 0;
            if (c < headers.size()) w = Math.max(w, safe(headers.get(c)).length());
            for (List<String> r : rows) {
                if (c < r.size()) w = Math.max(w, safe(r.get(c)).length());
            }
            widths[c] = Math.min(Math.max(w, 3), 48); // clamp to avoid very long columns
        }

        String sep = line(widths);
        StringBuilder sb = new StringBuilder();
        if (!headers.isEmpty()) {
            sb.append(sep).append('\n');
            sb.append(row(headers, widths)).append('\n');
        }
        sb.append(sep).append('\n');
        for (List<String> r : rows) {
            sb.append(row(r, widths)).append('\n');
        }
        sb.append(sep);
        return sb.toString();
    }

    private static String row(List<String> cols, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cols.size() ? safe(cols.get(i)) : "";
            sb.append(pad(cell, widths[i])).append(" | ");
        }
        return sb.toString();
    }

    private static String line(int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append("+");
        }
        return sb.toString();
    }

    private static String pad(String s, int w) {
        if (s.length() > w) s = s.substring(0, w - 1) + "…";
        return s + " ".repeat(Math.max(0, w - s.length()));
    }

    private static String safe(String s) { return s == null ? "" : s; }

    public static String listBullets(List<String> lines) {
        return lines.stream().map(l -> "• " + l).collect(Collectors.joining("\n"));
    }
}
