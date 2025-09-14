package com.example.productfilter.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/cart/sections")
public class CartSectionController {

    // ======= Хранилища в сессии =======
    private static final String SECTIONS = "sections";             // Map<Long, String>   (id -> name)
    private static final String SECTION_PARENT = "sectionParent";  // Map<Long, Long>     (id -> parentId)
    private static final String PRODUCT_SECTION = "productSection";// Map<Integer, Long>  (productId -> sectionId)
    private static final AtomicLong SEQ = new AtomicLong(1L);

    // DTO для дерева
    public record Node(Long id, String name, List<Node> children) {}

    // ------- ensure* -------
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
            map.put(1L, null); // у корня нет родителя
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

    // ======= Существующие эндпоинты (совместимость) =======

    // Плоский список (как раньше)
    @GetMapping
    public Map<Long,String> list(HttpSession s) {
        return sections(s);
    }

    // Привязка productId -> sectionId
    @GetMapping("/mapping")
    public Map<Integer,Long> mapping(HttpSession s) {
        return productSection(s);
    }

    // Создать раздел (теперь с необязательным parentId)
    @PostMapping
    public Map<String,Object> create(@RequestParam String name,
                                     @RequestParam(required = false) Long parentId,
                                     HttpSession s) {
        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);
        long id = nextId(s);

        Long p = (parentId != null ? parentId : 1L);
        if (!sec.containsKey(p)) p = 1L;

        sec.put(id, name);
        parents.put(id, p);
        return Map.of("id", id, "name", name, "parentId", p);
    }

    @PutMapping("/{id}")
    public Map<String,Object> rename(@PathVariable Long id, @RequestParam String name, HttpSession s) {
        Map<Long,String> sec = sections(s);
        if (sec.containsKey(id)) sec.put(id, name);
        return Map.of("ok", true);
    }

    // Удалить: детей «поднимаем» к родителю, товары — тоже к родителю
    @DeleteMapping("/{id}")
    public Map<String,Object> delete(@PathVariable Long id, HttpSession s) {
        if (Objects.equals(id, 1L))
            return Map.of("ok", false, "msg", "Нельзя удалить «Общий»");

        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);
        if (!sec.containsKey(id)) return Map.of("ok", true);

        Long parent = parents.get(id);
        if (parent == null) parent = 1L;

        // перепривязываем детей к родителю удаляемого
        for (Map.Entry<Long,Long> e : new ArrayList<>(parents.entrySet())) {
            if (Objects.equals(e.getValue(), id)) {
                parents.put(e.getKey(), parent);
            }
        }

        // перепривязываем товары к родителю удаляемого
        Map<Integer, Long> ps = productSection(s);
        for (Map.Entry<Integer, Long> e : ps.entrySet()) {
            if (Objects.equals(e.getValue(), id)) {
                e.setValue(parent);
            }
        }

        // удаляем сам раздел
        parents.remove(id);
        sec.remove(id);
        return Map.of("ok", true);
    }

    // Назначить товары в раздел
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

    // ======= Новые эндпоинты для ИЕРАРХИИ =======

    // Дерево
    @GetMapping("/tree")
    public List<Node> tree(HttpSession s) {
        Map<Long,String> sec = sections(s);
        Map<Long,Long> parents = sectionParent(s);

        // подготавливаем все узлы
        Map<Long, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<Long,String> e : sec.entrySet()) {
            nodes.put(e.getKey(), new Node(e.getKey(), e.getValue(), new ArrayList<>()));
        }

        // строим дерево
        List<Node> roots = new ArrayList<>();
        for (Map.Entry<Long, Node> e : nodes.entrySet()) {
            Long id = e.getKey();
            Node node = e.getValue();
            Long p = parents.get(id);

            if (p == null) {
                // корневой узел
                roots.add(node);
            } else {
                Node parent = nodes.get(p);
                if (parent != null) {
                    parent.children().add(node);
                } else {
                    // на случай «битого» parentId — считаем корневым
                    roots.add(node);
                }
            }
        }

        // гарантируем наличие корня «Общий»
        if (roots.stream().noneMatch(n -> Objects.equals(n.id(), 1L)) && nodes.containsKey(1L)) {
            roots.add(0, nodes.get(1L));
        }
        return roots;
    }

    // Переместить папку в другую папку
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

        // защита: нельзя сделать цикл (перенос в своего потомка)
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
}
