package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.util.Map;

public record ErrorDto(String message, Map<String, String> fieldErrors) {}
