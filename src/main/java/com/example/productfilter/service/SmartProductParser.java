package com.example.productfilter.service;

import java.util.regex.*;
import java.util.*;

public class SmartProductParser {

    public static class ParsedParams {
        public String param1 = "";
        public String param2 = "";
        public String param3 = "";
        public String param4 = "";
        public String param5 = "";

        @Override
        public String toString() {
            return String.format("param1=%s, param2=%s, param3=%s, param4=%s, param5=%s",
                    param1, param2, param3, param4, param5);
        }
    }

    public static ParsedParams parse(String name) {
        ParsedParams result = new ParsedParams();
        String lower = name.toLowerCase();

        // Размеры
        Matcher sizeMatcher = Pattern.compile("(\\d+)[xхХ](\\d+)[xхХ](\\d+)").matcher(lower);
        List<String> sizes = new ArrayList<>();
        if (sizeMatcher.find()) {
            sizes.add(sizeMatcher.group(1));
            sizes.add(sizeMatcher.group(2));
            sizes.add(sizeMatcher.group(3));
        }

        // Полюсы
        Matcher poles = Pattern.compile("\\b[1-4][pр]\\b").matcher(lower);
        if (poles.find()) result.param1 = poles.group(0).replace("р", "p").toUpperCase();

        // Ток
        Matcher current = Pattern.compile("\\d{1,3}\\s?[аaА]", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (current.find()) result.param2 = current.group(0).replace(" ", "").toUpperCase();

        // Отсечка
        Matcher cutoff = Pattern.compile("\\b\\d{1,2}[.,]?\\d{0,2}\\s?[кkKК][аaА]\\b").matcher(lower);
        if (cutoff.find()) {
            String cut = cutoff.group(0).replace(" ", "").toLowerCase();
            if (!cut.equalsIgnoreCase(result.param2)) result.param4 = cut;
        }

        // Характеристика (B, C, D)
        Matcher charac = Pattern.compile("(х-ка\\s)?\\b[BCDСDЗZ]\\b", Pattern.CASE_INSENSITIVE).matcher(name);
        if (charac.find()) result.param3 = charac.group(0).replace("х-ка", "").trim().toUpperCase();

        // Толщина
        Matcher thickness = Pattern.compile("(?<!\\d)([0-3][.,]\\d{1})(?!\\d)").matcher(name);
        if (thickness.find() && result.param4.isEmpty()) result.param4 = thickness.group(1).replace(",", ".");

        // Исполнение для лотков/труб
        List<String> perforationWords = Arrays.asList(
                "перфорированный", "перф.", "с боковой перфорацией", "с крышкой",
                "глухой", "без перфорации", "без боковой перфорации", "с перфорацией"
        );
        String matchedWord = "";
        for (String word : perforationWords) {
            if (lower.contains(word)) {
                matchedWord = word;
                break;
            }
        }
        if (lower.contains("лоток") || lower.contains("профиль") || lower.contains("переходник") || lower.contains("труба")) {
            result.param5 = matchedWord;
        }

        // Размеры в fallback
        if (result.param1.isEmpty() && sizes.size() > 0) result.param1 = sizes.get(0);
        if (result.param2.isEmpty() && sizes.size() > 1) result.param2 = sizes.get(1);
        if (result.param3.isEmpty() && sizes.size() > 2) result.param3 = sizes.get(2);

        return result;
    }

    public static void main(String[] args) {
        List<String> examples = Arrays.asList(
                "ВА47-60 3P C 10А 6кА IEK",
                "Лоток с боковой перфорацией 35х200х3000 1,5 HDZ",
                "Контактор КМИ-10910 9А 36В/АС3 1NO",
                "Труба глухая 100х100х200 0.7",
                "Автоматический выключатель 3P 63А (D) 4,5kA ВА 47-63 EKF PROxima",
                "ARMAT Авт. выкл. M06N 2P B 16А IEK",
                "Разветвитель лестничный LESTA Т-обр. 100х200мм R600 HDZ IEK",
                "SMART Корпус метал. сборный ВРУ-2 2000х800х600 IP54 IEK"
        );

        for (String name : examples) {
            ParsedParams parsed = parse(name);
            System.out.println(name);
            System.out.println("→ " + parsed);
        }
    }
}
