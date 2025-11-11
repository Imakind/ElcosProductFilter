package com.example.productfilter.repository;

import com.example.productfilter.model.ProposalNodeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ProposalNodeItemRepository extends JpaRepository<ProposalNodeItem, Long> {
    List<ProposalNodeItem> findByNode_Id(Long nodeId);
    void deleteByNode_Id(Long nodeId);
}
