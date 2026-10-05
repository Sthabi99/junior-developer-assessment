package com.enviro.assessment.junior.thabiso.kojoana.repository;
import com.enviro.assessment.junior.thabiso.kojoana.model.WithdrawalNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WithdrawalRepository extends JpaRepository<WithdrawalNotice, Long> {
    List<WithdrawalNotice> findByProductInvestorIdOrderByCreatedAtDescIdDesc(Long investorId);
}
