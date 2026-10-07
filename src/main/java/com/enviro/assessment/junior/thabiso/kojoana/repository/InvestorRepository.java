package com.enviro.assessment.junior.thabiso.kojoana.repository;

import com.enviro.assessment.junior.thabiso.kojoana.model.Investor;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data provides the usual save and find methods for investors.
public interface InvestorRepository extends JpaRepository<Investor, Long> {}
