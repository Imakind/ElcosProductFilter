package com.example.productfilter.repository;

import com.example.productfilter.model.ProposalSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProposalSectionRepository extends JpaRepository<ProposalSection, Long> {
    List<ProposalSection> findByProposalId(Long proposalId);
}
