package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.time.LocalDate;

// Only these fields are sent to the browser, not the JPA entities.
// Sends only the investor details the page needs.
public record InvestorDto(Long id, String name, LocalDate dateOfBirth, int age) {}
