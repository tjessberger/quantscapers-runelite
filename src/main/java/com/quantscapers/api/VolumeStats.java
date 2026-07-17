package com.quantscapers.api;

import lombok.Data;

/**
 * One entry from GET /24h or GET /5m. Average prices can be null when
 * nothing traded in that window; volumes default to 0.
 */
@Data
public class VolumeStats {
    private Double avgHighPrice;
    private long highPriceVolume;
    private Double avgLowPrice;
    private long lowPriceVolume;
}
