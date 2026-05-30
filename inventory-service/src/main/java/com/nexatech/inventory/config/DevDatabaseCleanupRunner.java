package com.nexatech.inventory.config;

import com.nexatech.inventory.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev.db.cleanup.enabled", havingValue = "true")
public class DevDatabaseCleanupRunner implements CommandLineRunner {

    private final ProcessedEventRepository processedEventRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("DevDatabaseCleanupRunner: Cleaning up processed_events table for a clean slate...");
        try {
            processedEventRepository.deleteAllInBatch();
            log.info("DevDatabaseCleanupRunner: Successfully cleared processed_events table.");
        } catch (Exception e) {
            log.error("DevDatabaseCleanupRunner: Failed to clear processed_events: {}", e.getMessage());
        }
    }
}
