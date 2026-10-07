package com.enviro.assessment.junior.thabiso.kojoana.repository;

import com.enviro.assessment.junior.thabiso.kojoana.model.WithdrawalNotice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

// Finds saved notices for the selected investor.
public interface WithdrawalRepository extends JpaRepository<WithdrawalNotice, Long> {
  List<WithdrawalNotice> findByProductInvestorIdOrderByCreatedAtDescIdDesc(Long investorId);
}
