package com.enviro.assessment.junior.thabiso.kojoana.repository;

import com.enviro.assessment.junior.thabiso.kojoana.model.InvestmentProduct;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

// Product queries are scoped to the investor; the withdrawal query below also takes a write lock.
public interface ProductRepository extends JpaRepository<InvestmentProduct, Long> {
  List<InvestmentProduct> findByInvestorIdOrderById(Long investorId);

  // This database lock lasts until the withdrawal transaction finishes.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InvestmentProduct p where p.id = :id")
  Optional<InvestmentProduct> findForWithdrawal(@Param("id") Long id);
}
