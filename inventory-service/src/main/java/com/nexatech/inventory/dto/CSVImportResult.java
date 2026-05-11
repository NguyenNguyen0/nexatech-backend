package com.nexatech.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CSVImportResult {
    private int successCount;
    private int failureCount;
    private int totalLines;
    private List<CSVImportError> errorLines;
}
