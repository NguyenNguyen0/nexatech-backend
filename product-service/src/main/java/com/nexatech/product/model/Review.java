package com.nexatech.product.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "reviews")
@CompoundIndex(name = "product_user_unique", def = "{'productId': 1, 'userId': 1}", unique = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @Id
    private String id;

    private String productId;

    private UUID userId;

    private int rating;

    private String title;

    private String content;

    private List<String> images;

    @Builder.Default
    private long helpfulCount = 0L;

    @CreatedDate
    private Instant createdAt;
}
