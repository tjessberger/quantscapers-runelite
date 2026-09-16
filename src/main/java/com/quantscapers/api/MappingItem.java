package com.quantscapers.api;

import lombok.Data;

/**
 * One entry from GET /mapping. `limit` and `highalch` are absent in the
 * wiki's JSON for many items, so both are boxed and must be null-checked.
 */
@Data
public class MappingItem {
    private int id;
    private String name;
    private Integer limit;
    private Integer highalch;

    public int limitOrZero() {
        return limit == null ? 0 : limit;
    }

    public int highalchOrZero() {
        return highalch == null ? 0 : highalch;
    }
}
