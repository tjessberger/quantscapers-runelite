package com.quantscapers.api;

import java.util.List;
import lombok.Data;

@Data
public class TimeseriesResponse {
    private List<TimeseriesPoint> data;
    private int itemId;
}
