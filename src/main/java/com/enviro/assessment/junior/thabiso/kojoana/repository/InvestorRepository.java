package com.enviro.assessment.junior.thabiso.kojoana.repository;

import com.enviro.assessment.junior.thabiso.kojoana.model.Investor;
import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository supplies save and findById, so these database operations need no manual SQL.
public interface InvestorRepository extends JpaRepository<Investor, Long> {}
