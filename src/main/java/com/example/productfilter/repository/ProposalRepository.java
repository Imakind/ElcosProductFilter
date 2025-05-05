package com.example.productfilter.repository;

import com.example.productfilter.dto.ProposalHistoryView;
import com.example.productfilter.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    List<Proposal> findBySessionIdAndFileType(String sessionId, String fileType);
    Optional<Proposal> findByIdAndSessionId(Long id, String sessionId);
    List<Proposal> findBySessionIdOrderByTimestampDesc(String sessionId);
    @Query("SELECT new com.example.productfilter.dto.ProposalHistoryView(p.id, p.name, p.totalSum, p.filePath, TO_CHAR(p.timestamp, 'YYYY-MM-DD HH24:MI')) " +
            "FROM Proposal p WHERE p.sessionId = :sessionId AND p.fileType = 'estimate' ORDER BY p.timestamp DESC")
    List<ProposalHistoryView> findEstimateHistoryBySessionId(@Param("sessionId") String sessionId);
    List<Proposal> findBySessionIdAndFileTypeOrderByTimestampDesc(String sessionId, String fileType);
    // ProposalRepository.java
    List<Proposal> findAllByOrderByTimestampDesc();



}
