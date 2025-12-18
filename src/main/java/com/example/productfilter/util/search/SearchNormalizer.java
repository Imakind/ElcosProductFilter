package com.example.productfilter.util.search;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public final class SearchNormalizer {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCT_OR_SEP = Pattern.compile("[\\p{Punct}«»“”„’`´•]+");

    // Латинские ↔ кириллические "похожие" символы (самый частый кейс в артикулах/брендах)
    private static final Map<Character, Character> LAT_TO_CYR = Map.ofEntries(
            Map.entry('a', 'а'),
            Map.entry('b', 'в'),
            Map.entry('c', 'с'),
            Map.entry('e', 'е'),
            Map.entry('h', 'н'),
            Map.entry('k', 'к'),
            Map.entry('m', 'м'),
            Map.entry('o', 'о'),
            Map.entry('p', 'р'),
            Map.entry('t', 'т'),
            Map.entry('x', 'х'),
            Map.entry('y', 'у')
    );

    private static final Map<Character, Character> CYR_TO_LAT = invert(LAT_TO_CYR);

    private static Map<Character, Character> invert(Map<Character, Character> src) {
        Map<Character, Character> res = new HashMap<>();
        for (var e : src.entrySet()) res.put(e.getValue(), e.getKey());
        return res;
    }

    /** Нормализация общего текста (имя товара/keyword) */
    public String normalizeText(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replace('ё', 'е');

        // пунктуацию -> пробел
        s = PUNCT_OR_SEP.matcher(s).replaceAll(" ");
        // спец-разделители тоже в пробел
        s = s.replace('/', ' ').replace('\\', ' ').replace('_', ' ').replace('-', ' ');

        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /** Нормализация для артикула: оставляем только [a-zа-я0-9], убираем пробелы/дефисы */
    public String normalizeArticle(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT);
        s = s.replace('ё', 'е');
        s = s.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", ""); // только буквы/цифры
        return s;
    }

    /** Нормализация для даты (как строка): убираем всё кроме цифр */
    public String normalizeDateDigits(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[^0-9]+", "");
    }

    /** Варианты запроса: базовый + без пробелов + лат<->кир похожие */
    public List<String> variantsText(String raw) {
        String base = normalizeText(raw);
        if (base.isBlank()) return List.of();

        String noSpace = base.replace(" ", "");

        String swappedLatToCyr = swapLookalikes(base, LAT_TO_CYR);
        String swappedCyrToLat = swapLookalikes(base, CYR_TO_LAT);

        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(base);
        out.add(noSpace);

        if (!swappedLatToCyr.equals(base)) {
            out.add(swappedLatToCyr);
            out.add(swappedLatToCyr.replace(" ", ""));
        }
        if (!swappedCyrToLat.equals(base)) {
            out.add(swappedCyrToLat);
            out.add(swappedCyrToLat.replace(" ", ""));
        }

        // отфильтруем очень короткое
        out.removeIf(v -> v.length() < 2);

        return new ArrayList<>(out);
    }

    private String swapLookalikes(String s, Map<Character, Character> map) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            sb.append(map.getOrDefault(ch, ch));
        }
        return sb.toString();
    }
}
