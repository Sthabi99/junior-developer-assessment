package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.util.Map;

// Only these fields are sent to the browser, not the JPA entities.
public record ErrorDto(String message, Map<String, String> fieldErrors) {}
