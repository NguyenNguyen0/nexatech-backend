package com.nexatech.inventory.service;

import com.nexatech.inventory.dto.InventoryResponse;
import com.nexatech.inventory.dto.StockCheckRequest;
import com.nexatech.inventory.dto.StockUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    public InventoryResponse getStock(String productId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public InventoryResponse updateStock(StockUpdateRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean checkStock(StockCheckRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void reserveStock(StockCheckRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void releaseStock(StockCheckRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<InventoryResponse> getLowStockItems() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
