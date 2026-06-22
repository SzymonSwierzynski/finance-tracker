package com.financetracker.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Standard paginated list envelope: {@code { items, page, size, total }} (see CLAUDE.md §4). */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

  /** Wrap a Spring Data {@link Page} of already-mapped items. */
  public static <T> PageResponse<T> of(List<T> items, Page<?> page) {
    return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
