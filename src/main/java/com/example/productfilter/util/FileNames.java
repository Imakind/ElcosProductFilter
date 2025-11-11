package com.example.productfilter.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class FileNames {
    private static final ZoneId KZ = ZoneId.of("Asia/Almaty");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private FileNames(){}

    public static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null ? a.getName() : "anonymous";
    }

    public static String today() {
        return LocalDate.now(KZ).format(DATE);
    }

    public static String safe(String s) {
        if (s == null) return "";
        // запрещённые для имён символы → пробел
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]+", " ").trim();
        // убрать двойные пробелы
        return cleaned.replaceAll("\\s{2,}", " ");
    }

    /** Для заголовка Content-Disposition (RFC 5987) */
    public static String encodeRFC5987(String filename) {
        return "filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8);
    }

    public static String smeta(String project, String user) {
        return String.format("Elcos Смета %s %s %s.xlsx", safe(project), today(), safe(user));
    }

    public static String kpPdf(String project, String user) {
        return String.format("Elcos КП %s %s %s.pdf", safe(project), today(), safe(user));
    }

    public static String kpXlsx(String project, String user) {
        return String.format("Elcos КП %s %s %s.xlsx", safe(project), today(), safe(user));
    }
}
