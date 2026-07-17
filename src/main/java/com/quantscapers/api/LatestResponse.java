package com.quantscapers.api;

import java.util.Map;
import lombok.Data;

@Data
public class LatestResponse {
    private Map<Integer, PriceQuote> data;
}
