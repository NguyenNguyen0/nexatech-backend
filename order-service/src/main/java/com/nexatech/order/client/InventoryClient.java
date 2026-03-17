package com.nexatech.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventory-service", path = "/api/v1/inventory")
public interface InventoryClient {

    @PostMapping("/check")
    Object checkStock(@RequestBody Object request);

    @PostMapping("/reserve")
    Object reserveStock(@RequestBody Object request);
}
