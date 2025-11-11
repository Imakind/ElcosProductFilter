package com.example.productfilter.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/cart/sections")
public class CartSectionController {

    // ======= Ключи в сессии =======
    private static final String SECTIONS = "sections";            // Map<Long, String>   (id -> name)
    private static final String SECTION_PARENT = "sectionParent"; // Map<Long, Long>     (id -> parentId)
    private static final String PRODUCT_SECTION = "productSection";// Map<Integer, Long> (productId -> sectionId)
    private static final AtomicLong SEQ = new AtomicLong(1L);

    // Узел дерева
    public record Node(Long id, String name, List<Node> children) { }

    // ======= Хелперы для сессии =======
    @SuppressWarnings("unchecked")
    private Map<Long,String> sections(HttpSession s) {
        Map<Long,String> map = (Map<Long,String>) s.getAttribute(SECTIONS);
        if (map == null) {
            map = new LinkedHashMap<>();
            map.put(1L, "Общий"); // корень
            s.setAttribute(SECTIONS, map);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Long,Long> sectionParent(HttpSession s) {
        Map<Long,Long> map = (Map<Long,Long>) s.getAttribute(SECTION_PARENT);
        if (map == null) {
            map = new HashMap<>();
            map.put(1L, null); // корень без родителя
            s.setAttribute(SECTION_PARENT, map);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Long> productSection(HttpSession s) {
        Map<Integer, Long> map = (Map<Integer, Long>) s.getAttribute(PRODUCT_SECTION);
        if (map == null) {
            map = new HashMap<>();
            s.setAttribute(PRODUCT_SECTION, map);
        }
        return map;
    }

    private long nextId(HttpSession s) {
        long max = sections(s).keySet().stream().mapToLong(Long::longValue).max().orElse(1L);
        return Math.max(max + 1, SEQ.incrementAndGet());
    }

    // ======= Эндпоинты =======

    /** Современный список: id, name, parentId (рекомендую использовать его на фронте) */
    @GetMapping
    public List<Map<String, Object>> listWithParents(HttpSession s) {
        Map<Long, String> sec = sections(s);
        Map<Long, Long> parents = sectionParent(s);

        List<Map<String, Object>> out = new ArrayList<>(sec.size());
        for (Map.Entry<Long, String> e : sec.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getKey());
            m.put("name", e.getValue());
            m.put("parentId", parents.get(e.getKey()));
            out.add(m);
        }
        return out;
    }

    /** Старый плоский список (для обратной совместимости) */
    @GetMapping("/flat")
    public Map<Long,String> flat(HttpSession s) {
        return sections(s);
    }

    /** Привязка productId -> sectionId */
    @GetMapping("/mapping")
    public Map<Integer,Long> mapping(HttpSession s) {
        return productSection(s);
    }

    /** Создать раздел (опционально указать parentId, иначе — корень 1) */
    @PostMapping
    public Map<String,Object> create(@RequestParam String name,
                                     @RequestParam(required = false) Long parentId,
                                     HttpSession s) {
        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);

        Long p = (parentId != null && sec.containsKey(parentId)) ? parentId : 1L;
        long id = nextId(s);

        sec.put(id, name);
        parents.put(id, p);

        return Map.of("id", id, "name", name, "parentId", p);
    }

    /** Переименовать раздел */
    @PutMapping("/{id}")
    public Map<String,Object> rename(@PathVariable Long id, @RequestParam String name, HttpSession s) {
        Map<Long,String> sec = sections(s);
        if (sec.containsKey(id)) sec.put(id, name);
        return Map.of("ok", true);
    }

    /** Удалить раздел: дети и товары перепривязываются к его родителю */
    @DeleteMapping("/{id}")
    public Map<String,Object> delete(@PathVariable Long id, HttpSession s) {
        if (Objects.equals(id, 1L))
            return Map.of("ok", false, "msg", "Нельзя удалить «Общий»");

        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);
        if (!sec.containsKey(id)) return Map.of("ok", true);

        Long parent = parents.get(id);
        if (parent == null) parent = 1L;

        // перепривязка детей
        for (Map.Entry<Long,Long> e : new ArrayList<>(parents.entrySet())) {
            if (Objects.equals(e.getValue(), id)) {
                parents.put(e.getKey(), parent);
            }
        }

        // перепривязка товаров
        Map<Integer, Long> ps = productSection(s);
        for (Map.Entry<Integer, Long> e : ps.entrySet()) {
            if (Objects.equals(e.getValue(), id)) {
                e.setValue(parent);
            }
        }

        parents.remove(id);
        sec.remove(id);
        return Map.of("ok", true);
    }

    /** Назначить товары в раздел */
    @PostMapping("/assign")
    public Map<String,Object> assign(@RequestParam Long sectionId,
                                     @RequestParam("productIds") List<Integer> productIds,
                                     HttpSession s) {
        Map<Long,String> sec = sections(s);
        if (!sec.containsKey(sectionId)) return Map.of("ok", false, "msg", "Раздел не найден");
        Map<Integer, Long> ps = productSection(s);
        for (Integer pid : productIds) ps.put(pid, sectionId);
        return Map.of("ok", true);
    }

    @GetMapping("/tree")
    public List<Node> tree(HttpSession s) {
        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);

        Map<Long, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<Long,String> e : sec.entrySet()) {
            nodes.put(e.getKey(), new Node(e.getKey(), e.getValue(), new ArrayList<>()));
        }

        List<Node> roots = new ArrayList<>();
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            Long id = e.getKey();
            Node node = e.getValue();
            Long p = parents.get(id);
            if (p == null) {
                roots.add(node);
            } else {
                Node parent = nodes.get(p);
                if (parent != null) parent.children().add(node);
                else roots.add(node);
            }
        }
        return roots;
    }



    /** Переместить раздел в другой раздел (с защитой от циклов) */
    @PostMapping("/move")
    public Map<String,Object> move(@RequestParam Long sectionId,
                                   @RequestParam Long newParentId,
                                   HttpSession s) {
        if (Objects.equals(sectionId, 1L))
            return Map.of("ok", false, "msg", "Нельзя перемещать «Общий»");

        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);
        if (!sec.containsKey(sectionId) || !sec.containsKey(newParentId))
            return Map.of("ok", false, "msg", "Раздел не найден");

        if (isDescendant(newParentId, sectionId, parents)) {
            return Map.of("ok", false, "msg", "Нельзя переместить раздел внутрь самого себя");
        }
        parents.put(sectionId, newParentId);
        return Map.of("ok", true);
    }

    private boolean isDescendant(Long node, Long potentialAncestor, Map<Long,Long> parents) {
        Long cur = parents.get(node);
        while (cur != null) {
            if (Objects.equals(cur, potentialAncestor)) return true;
            cur = parents.get(cur);
        }
        return false;
    }

    /** Явно сменить родителя (если удобно на фронте) */
    @PostMapping("/set-parent")
    public Map<String,Object> setSectionParent(@RequestParam("sectionId") Long sectionId,
                                               @RequestParam(value = "parentId", required = false) Long parentId,
                                               HttpSession s) {
        Map<Long, String> sec = sections(s);
        Map<Long, Long> parents = sectionParent(s);
        if (!sec.containsKey(sectionId)) return Map.of("ok", false, "msg", "Неизвестная секция");
        if (parentId != null && !sec.containsKey(parentId)) return Map.of("ok", false, "msg", "Неизвестный родитель");
        if (Objects.equals(sectionId, 1L)) return Map.of("ok", false, "msg", "Нельзя менять родителя «Общий»");
        if (parentId != null && isDescendant(parentId, sectionId, parents))
            return Map.of("ok", false, "msg", "Нельзя сделать цикл");
        parents.put(sectionId, parentId == null ? 1L : parentId);
        return Map.of("ok", true);
    }

    @PostMapping("/clear")
    public Map<String,Object> clearSection(@RequestParam Long sectionId, HttpSession s) {
        return doClearSection(sectionId, s);
    }

    @PostMapping("/{id}/clear")
    public Map<String,Object> clearSectionByPath(@PathVariable("id") Long id, HttpSession s) {
        return doClearSection(id, s);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> doClearSection(Long sectionId, HttpSession s) {
        Map<Long,String> sec = sections(s);
        if (sectionId == null || !sec.containsKey(sectionId)) {
            return Map.of("ok", false, "msg", "Раздел не найден");
        }

        // Текущая корзина и коэффициенты в сессии
        Map<Integer, Integer> cart = (Map<Integer, Integer>) s.getAttribute("cart");
        Map<Integer, Double> coeff = (Map<Integer, Double>) s.getAttribute("coefficientMap");
        Map<Integer, Long> ps = productSection(s); // productId -> sectionId (может не содержать id => считаем 1L)

        if (cart == null || cart.isEmpty()) {
            return Map.of("ok", true, "removed", List.of());
        }

        List<Integer> removed = new ArrayList<>();

        // Чистим ТОЛЬКО товары из этой папки (без рекурсии по подпапкам)
        for (Integer pid : new ArrayList<>(cart.keySet())) {
            Long sid = ps.get(pid);
            if (sid == null) sid = 1L;          // не задано => «Общий»
            if (Objects.equals(sid, sectionId)) {
                cart.remove(pid);
                if (coeff != null) coeff.remove(pid);
                ps.remove(pid);                 // убираем и маппинг папки
                removed.add(pid);
            }
        }

        return Map.of("ok", true, "removed", removed);
    }


}