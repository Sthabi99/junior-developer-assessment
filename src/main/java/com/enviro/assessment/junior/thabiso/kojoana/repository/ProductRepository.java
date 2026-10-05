package com.enviro.assessment.junior.thabiso.kojoana.repository;
import com.enviro.assessment.junior.thabiso.kojoana.model.InvestmentProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ProductRepository extends JpaRepository<InvestmentProduct, Long> {
    List<InvestmentProduct> findByInvestorIdOrderById(Long investorId);
    // Lock this balance until the withdrawal transaction finishes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InvestmentProduct p where p.id = :id")
    Optional<InvestmentProduct> findForWithdrawal(@Param("id") Long id);
}
