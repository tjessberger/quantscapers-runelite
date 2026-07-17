package com.quantscapers.api;

import lombok.Data;

/** One hourly bucket from GET /timeseries?timestep=1h. */
@Data
public class TimeseriesPoint {
    private long timestamp;
    private Double avgHighPrice;
    private Double avgLowPrice;
    private Long highPriceVolume;
    private Long lowPriceVolume;
}
