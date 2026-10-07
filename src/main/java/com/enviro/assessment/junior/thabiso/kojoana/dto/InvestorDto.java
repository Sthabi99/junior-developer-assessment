package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.time.LocalDate;

// The account selector and heading use this ID, name, date of birth and calculated age.
public record InvestorDto(Long id, String name, LocalDate dateOfBirth, int age) {}
