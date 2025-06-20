package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDataResponse {
    private boolean success;
    private String message;
    private Map<String, List<? extends Number>> features;
}