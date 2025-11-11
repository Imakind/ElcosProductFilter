package com.example.productfilter.repository;

import com.example.productfilter.model.ProposalNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProposalNodeRepository extends JpaRepository<ProposalNode, Long> {
    List<ProposalNode> findByProposal_IdOrderBySortOrderAscIdAsc(Long proposalId);
    List<ProposalNode> findByParent_IdOrderBySortOrderAscIdAsc(Long parentId);
    Optional<ProposalNode> findByIdAndProposal_Id(Long id, Long proposalId);
}
