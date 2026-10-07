package com.enviro.assessment.junior.thabiso.kojoana.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
// Stores the name and date of birth used to calculate the investor age.
public class Investor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private LocalDate dateOfBirth;

  protected Investor() {} // JPA needs an empty constructor.

  public Investor(String name, LocalDate dateOfBirth) {
    this.name = name;
    this.dateOfBirth = dateOfBirth;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }
}
