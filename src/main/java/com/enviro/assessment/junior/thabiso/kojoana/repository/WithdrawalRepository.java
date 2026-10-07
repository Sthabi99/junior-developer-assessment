package com.enviro.assessment.junior.thabiso.kojoana.repository;

import com.enviro.assessment.junior.thabiso.kojoana.model.WithdrawalNotice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// The query returns the investor's notices newest first, using the ID to order equal timestamps.
public interface WithdrawalRepository extends JpaRepository<WithdrawalNotice, Long> {
  List<WithdrawalNotice> findByProductInvestorIdOrderByCreatedAtDescIdDesc(Long investorId);
}
