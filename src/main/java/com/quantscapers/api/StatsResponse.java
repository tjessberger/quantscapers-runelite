package com.quantscapers.api;

import java.util.Map;
import lombok.Data;

/** Shape shared by GET /24h and GET /5m. */
@Data
public class StatsResponse {
    private Map<Integer, VolumeStats> data;
    private long timestamp;
}
