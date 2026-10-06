package com.quantscapers.api;

import lombok.Data;

/**
 * One entry from GET /latest. All four fields can be null when the wiki
 * has no recent trade data for the item.
 */
@Data
public class PriceQuote {
    private Long high;
    private Long highTime;
    private Long low;
    private Long lowTime;
}
