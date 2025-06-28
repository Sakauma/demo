package com.demo.dto;

import lombok.Data;

/**
 * 数据传输对象 (DTO)配置类
 * 使用 Lombok 的 @Data 注解自动生成 getter、setter、toString、equals 和 hashCode 方法。
 */
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