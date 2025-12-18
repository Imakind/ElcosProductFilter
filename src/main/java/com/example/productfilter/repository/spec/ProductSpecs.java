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
            if (rawKeyword == null || rawKeyword.isBlank()) return cb.conjunction();

            List<Predicate> ors = new ArrayList<>();

            // ---- NAME (LIKE) + NAME без пробелов ----
            List<String> variants = normalizer.variantsText(rawKeyword);

            Expression<String> nameSafe = cb.coalesce(root.get("name"), "");
            Expression<String> nameLower = cb.lower(nameSafe);

            Expression<String> nameNoSpace = cb.function(
                    "replace",
                    String.class,
                    nameLower,
                    cb.literal(" "),
                    cb.literal("")
            );

            for (String v : variants) {
                String like = "%" + escapeLike(v.toLowerCase()) + "%";
                ors.add(cb.like(nameLower, like, '\\'));

                String vNoSpace = v.replace(" ", "");
                if (!vNoSpace.isBlank()) {
                    String likeNoSpace = "%" + escapeLike(vNoSpace.toLowerCase()) + "%";
                    ors.add(cb.like(nameNoSpace, likeNoSpace, '\\'));
                }
            }

            // ---- ARTICLE (нормализованный contains) ----
            String art = normalizer.normalizeArticle(rawKeyword);
            if (!art.isBlank()) {
                Expression<String> artSafe = cb.coalesce(root.get("articleCode"), "");
                Expression<String> artLower = cb.lower(artSafe);

                Expression<String> artNorm = cb.function(
                        "replace",
                        String.class,
                        cb.function("replace", String.class, artLower, cb.literal("-"), cb.literal("")),
                        cb.literal(" "),
                        cb.literal("")
                );

                ors.add(cb.like(artNorm, "%" + escapeLike(art) + "%", '\\'));
            }

            // ---- DATE (если поле importPriceDate у тебя String) ----
            String dateDigits = normalizer.normalizeDateDigits(rawKeyword);
            if (dateDigits.length() >= 6) {
                Expression<String> dateField = cb.coalesce(root.get("importPriceDate"), "");

                Expression<String> dateNorm = cb.function(
                        "replace",
                        String.class,
                        cb.function(
                                "replace",
                                String.class,
                                cb.function(
                                        "replace",
                                        String.class,
                                        cb.function("replace", String.class, dateField, cb.literal("."), cb.literal("")),
                                        cb.literal(":"),
                                        cb.literal("")
                                ),
                                cb.literal("-"),
                                cb.literal("")
                        ),
                        cb.literal(" "),
                        cb.literal("")
                );

                ors.add(cb.like(dateNorm, "%" + escapeLike(dateDigits) + "%", '\\'));
            }

            return ors.isEmpty() ? cb.conjunction() : cb.or(ors.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
