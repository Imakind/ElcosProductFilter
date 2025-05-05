package com.example.productfilter.service;

import com.example.productfilter.dto.ProposalHistoryView;
import com.example.productfilter.model.Proposal;
import com.example.productfilter.model.ProposalItem;
import com.example.productfilter.repository.ProposalRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProposalService {
    private final ProposalRepository proposalRepository;

    public ProposalService(ProposalRepository proposalRepository) {
        this.proposalRepository = proposalRepository;
    }



    public void loadEstimateToSession(Long proposalId, HttpSession session) {
        Optional<Proposal> optionalProposal = proposalRepository.findById(proposalId);
        if (optionalProposal.isPresent()) {
            Proposal proposal = optionalProposal.get();
            Map<Integer, Integer> cart = new HashMap<>();
            Map<Integer, Double> coefficients = new HashMap<>();

            for (ProposalItem item : proposal.getItems()) {
                cart.put(item.getProduct().getProductId(), item.getQuantity());
                coefficients.put(item.getProduct().getProductId(), item.getCoefficient());
            }

            session.setAttribute("cart", cart);
            session.setAttribute("coefficientMap", coefficients);
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
