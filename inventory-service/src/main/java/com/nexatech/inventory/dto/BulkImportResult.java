package com.nexatech.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkImportResult {

    private int successCount;
    private int failureCount;
    private List<String> errors;
}
