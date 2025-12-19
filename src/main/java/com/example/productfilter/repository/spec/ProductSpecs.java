package com.example.productfilter.repository.spec;

import com.example.productfilter.model.Product;
import com.example.productfilter.util.search.SearchNormalizer;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ProductSpecs {

    private ProductSpecs() {}

    public static Specification<Product> idIn(Set<Integer> ids) {
        return (root, query, cb) -> {
            if (ids == null || ids.isEmpty()) return cb.disjunction();
            return root.get("productId").in(ids);
        };
    }

    /** keyword ищет по имени, артикулу, строке даты (importPriceDate) */
    public static Specification<Product> keywordElastic(String rawKeyword, SearchNormalizer normalizer) {
        return (root, query, cb) -> {
            if (rawKeyword == null || rawKeyword.isBlank())
                return cb.conjunction();

            List<Predicate> ors = new ArrayList<>();

            // =================
            // 🔥 БАЗОВАЯ НОРМАЛИЗАЦИЯ
            // =================
            String kw = rawKeyword.toLowerCase();

            // убираем . , () и прочий мусор
            kw = kw.replaceAll("[.,()]", " ");

            // любые -_/ → пробел
            kw = kw.replaceAll("[-_/]+", " ");

            // схлопываем пробелы
            kw = kw.replaceAll("\\s+", " ").trim();

            if (kw.isBlank())
                return cb.conjunction();

            // версия без пробелов
            String kwJoined = kw.replace(" ", "");

            Expression<String> name = cb.lower(cb.coalesce(root.get("name"), ""));
            Expression<String> nameNoDots =
                    cb.function("replace", String.class,
                            cb.function("replace", String.class,
                                    cb.function("replace", String.class,
                                            name,
                                            cb.literal("."), cb.literal("")
                                    ),
                                    cb.literal("-"), cb.literal(" ")
                            ),
                            cb.literal("_"), cb.literal(" ")
                    );

            Expression<String> nameJoined =
                    cb.function("replace", String.class, nameNoDots,
                            cb.literal(" "), cb.literal("")
                    );


            // ============================
            // ✅ УРОВЕНЬ 1 — ЖЁСТКОЕ СОВПАДЕНИЕ
            // ============================
            ors.add(cb.equal(nameJoined, kwJoined));

            // ============================
            // ✅ УРОВЕНЬ 2 — НОРМАЛИЗОВАННОЕ СОВПАДЕНИЕ
            // ============================
            ors.add(cb.like(nameJoined, "%" + escapeLike(kwJoined) + "%", '\\'));

            // ============================
            // ✅ УРОВЕНЬ 3 — МЯГКОЕ (НО АККУРАТНОЕ)
            // ============================
            // поиск по словам (чтобы 2в != 32в)
            ors.add(cb.like(cb.concat(" ", cb.concat(nameNoDots, " ")),
                    "% " + escapeLike(kw) + " %", '\\'));

            ors.add(cb.like(nameNoDots, kw + "%", '\\'));

            // ============================
            // ARTICLE
            // ============================
            String art = normalizer.normalizeArticle(rawKeyword);
            if (!art.isBlank()) {
                Expression<String> artSafe = cb.lower(cb.coalesce(root.get("articleCode"), ""));
                Expression<String> artNorm =
                        cb.function("replace", String.class,
                                cb.function("replace", String.class,
                                        artSafe,
                                        cb.literal("-"), cb.literal("")
                                ),
                                cb.literal(" "), cb.literal("")
                        );

                ors.add(cb.like(artNorm, "%" + escapeLike(art) + "%", '\\'));
            }

            // ============================
            // DATE DIGITS
            // ============================
            String digits = normalizer.normalizeDateDigits(rawKeyword);
            if (digits.length() >= 6) {
                Expression<String> dateField = cb.coalesce(root.get("importPriceDate"), "");

                Expression<String> dateNorm =
                        cb.function("replace", String.class,
                                cb.function("replace", String.class,
                                        cb.function("replace", String.class,
                                                cb.function("replace", String.class,
                                                        dateField,
                                                        cb.literal("."), cb.literal("")
                                                ),
                                                cb.literal(":"), cb.literal("")
                                        ),
                                        cb.literal("-"), cb.literal("")
                                ),
                                cb.literal(" "), cb.literal("")
                        );

                ors.add(cb.like(dateNorm, "%" + escapeLike(digits) + "%", '\\'));
            }

            return ors.isEmpty() ? cb.conjunction() : cb.or(ors.toArray(new Predicate[0]));
        };
    }


    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
