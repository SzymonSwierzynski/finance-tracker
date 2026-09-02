package com.financetracker.importing.dto;

import java.util.Map;

/**
 * What auto-detection concluded, for the UI banner. {@code recognizedColumns} maps a role
 * ("date"/"amount"/"description"/…) to the raw header label it was read from.
 */
public record DetectionInfo(
    String encoding, Integer headerRowIndex, Map<String, String> recognizedColumns) {}
