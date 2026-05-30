package com.nexatech.product.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(collection = "reviews")
@CompoundIndex(name = "product_user_idx", def = "{'productId': 1, 'userId': 1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Review {

    @MongoId(FieldType.STRING)
    private String id;

    private String productId;

    private UUID userId;

    private int rating;

    private String title;

    private String content;

    private List<String> images;

    private List<String> videos;

    @Builder.Default
    private long helpfulCount = 0L;

    @CreatedDate
    private Instant createdAt;
}
