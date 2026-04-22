package edu.iuh.fit.se.commonservice.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable DTO wrapper for paginated responses.
 * Replaces direct PageImpl serialization to avoid
 * Spring Data's unstable JSON structure warning.
 */
@Data
public class PagedResponse<T> {
    private List<T> content;
    private int number;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public PagedResponse(Page<T> page) {
        this.content = page.getContent();
        this.number = page.getNumber();
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.first = page.isFirst();
        this.last = page.isLast();
    }
}
