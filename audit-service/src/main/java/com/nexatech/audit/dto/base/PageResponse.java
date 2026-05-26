package com.nexatech.audit.dto.base;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> {

    private List<T> content;

    int page;
    int size;
    long totalElements;
    int totalPages;
    boolean hasMore;

    public PageResponse(Page<@NonNull T> page) {
        this.content = page.getContent();
        this.page = page.getNumber() + 1;
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.hasMore = this.page < this.totalPages;
    }
}
