package com.example.productfilter.service;

import com.example.productfilter.dto.ProposalHistoryView;
import com.example.productfilter.model.Proposal;
import com.example.productfilter.model.ProposalItem;
import com.example.productfilter.model.ProposalSection;
import com.example.productfilter.repository.ProposalRepository;
import com.example.productfilter.repository.ProposalSectionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProposalService {
    private final ProposalRepository proposalRepository;
    private final ProposalSectionRepository proposalSectionRepository;

    public ProposalService(ProposalRepository proposalRepository,
                           ProposalSectionRepository proposalSectionRepository) {
        this.proposalRepository = proposalRepository;
        this.proposalSectionRepository = proposalSectionRepository;
    }

    // --- ключи те же, что в CartSectionController ---
    private static final String SECTIONS = "sections";             // Map<Long,String>
    private static final String SECTION_PARENT = "sectionParent";  // Map<Long,Long>
    private static final String PRODUCT_SECTION = "productSection";// Map<Integer,Long>

    @SuppressWarnings("unchecked")
    private Map<Long, String> sections(HttpSession s) {
        Map<Long, String> map = (Map<Long, String>) s.getAttribute(SECTIONS);
        if (map == null) {
            map = new LinkedHashMap<>();
            s.setAttribute(SECTIONS, map);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> sectionParent(HttpSession s) {
        Map<Long, Long> map = (Map<Long, Long>) s.getAttribute(SECTION_PARENT);
        if (map == null) {
            map = new HashMap<>();
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

    private long nextId(Map<Long, String> sec) {
        return sec.keySet().stream().mapToLong(Long::longValue).max().orElse(1L) + 1L;
    }

    /**
     * Найти/создать в сессионном дереве путь ["Тест", "Подпапка"] и вернуть id последнего узла
     */
    private Long ensureSectionByPath(HttpSession s, List<String> path) {
        Map<Long, String> sec = sections(s);
        Map<Long, Long> parents = sectionParent(s);

        // корень
        if (!sec.containsKey(1L)) sec.put(1L, "Общий");
        if (!parents.containsKey(1L)) parents.put(1L, null);

        Long current = 1L;
        for (String name : path) {
            // ищем ребёнка с таким именем под current
            final Long parentIdFinal = current;
            final String nameFinal   = name;
            Long found = sec.entrySet().stream()
                    .filter(e -> Objects.equals(parents.get(e.getKey()), parentIdFinal) && name.equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .findFirst().orElse(null);

            if (found == null) {
                long id = nextId(sec);
                sec.put(id, name);
                parents.put(id, current);
                current = id;
            } else {
                current = found;
            }
        }
        return current;
    }

    private static List<String> buildPathNames(ProposalSection node) {
        List<String> path = new ArrayList<>();
        ProposalSection cur = node;
        while (cur != null) {
            path.add(cur.getName());
            cur = cur.getParent();
        }
        Collections.reverse(path); // от корня к листу
        return path;
    }

    /**
     * ВОССТАНОВЛЕНИЕ из БД: cart, coeff + дерево папок и мэппинг товар→папка
     */
    public void loadEstimateToSession(Long proposalId, HttpSession session) {
        Proposal proposal = proposalRepository.findById(proposalId).orElseThrow();

        // 1) Количество и коэффициенты — как раньше
        Map<Integer, Integer> cart = new HashMap<>();
        Map<Integer, Double> coefficients = new HashMap<>();

        for (ProposalItem item : proposal.getItems()) {
            cart.put(item.getProduct().getProductId(), item.getQuantity());
            coefficients.put(item.getProduct().getProductId(),
                    item.getCoefficient() != null ? item.getCoefficient() : 1.0);
        }
        session.setAttribute("cart", cart);
        session.setAttribute("coefficientMap", coefficients);

        // 2) Папки и мэппинг
        Map<Long, String> sec = sections(session);
        Map<Long, Long> parents = sectionParent(session);
        Map<Integer, Long> prodMap = productSection(session);

        // Полностью заменяем текущее дерево папок содержимым из сметы
        sec.clear();
        parents.clear();
        prodMap.clear();
        sec.put(1L, "Общий");
        parents.put(1L, null);

        // Пройдём по всем секциям сметы и построим в сессии такой же путь (по именам)
        // (Создавать руками дерево можно и по списку, но имён достаточно и надёжно).
        Map<Long, Long> savedSectionIdToSessionId = new HashMap<>();

        // Чтобы не вызывать лишних запросов, можно идти только по секциям, которые реально используются в items.
        for (ProposalItem item : proposal.getItems()) {
            Long savedSecId = item.getSectionId(); // тот самый "raw" столбец
            Long sessionSecId;
            if (savedSecId != null) {
                // найдём узел и восстановим путь
                ProposalSection node = item.getSectionNode();
                if (node == null) {
                    // подстраховка: загрузим по репозиторию, если не проинициализировался
                    // (опционально, если LAZY иногда не тащит)
                    // node = proposalSectionRepository.findById(savedSecId).orElse(null);
                }
                if (node != null) {
                    sessionSecId = savedSectionIdToSessionId.computeIfAbsent(savedSecId, id -> {
                        List<String> path = buildPathNames(node); // ["Тест", "Подпапка"]
                        return ensureSectionByPath(session, path);
                    });
                } else {
                    sessionSecId = 1L;
                }
            } else {
                sessionSecId = 1L;
            }
            prodMap.put(item.getProduct().getProductId(), sessionSecId);
        }
    }

    public List<ProposalHistoryView> getAllProposals() {
        List<Proposal> proposals = proposalRepository.findAllByOrderByTimestampDesc();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        return proposals.stream().map(p -> new ProposalHistoryView(
                p.getId(),
                p.getName(),
                p.getTotalSum(),
                p.getFilePath(),
                p.getTimestamp() != null ? p.getTimestamp().format(formatter) : ""
        )).collect(Collectors.toList());
    }
}