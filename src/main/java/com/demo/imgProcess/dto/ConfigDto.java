package com.demo.imgProcess.dto;

import lombok.Data;

@Data
public class ConfigDto {
    private Region region;
    private Algorithm algorithm;

    @Data
    public static class Region {
        private int x;
        private int y;
        private int width;
        private int height;
    }

    @Data
    public static class Algorithm {
        private double lr;
    }
}