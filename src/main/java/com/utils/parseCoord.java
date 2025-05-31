package com.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class parseCoord {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static Map<String, Integer> parse(String cropDataJson) {
        Map<String, Integer> coordinates = new HashMap<>();
        if (cropDataJson != null && !cropDataJson.isEmpty()) {
            try {
                JsonNode cropData = objectMapper.readTree(cropDataJson);
                int width = cropData.get("width").asInt();
                int height = cropData.get("height").asInt();
                int left = cropData.get("left").asInt();
                int top = cropData.get("top").asInt();
                coordinates.put("width", width);
                coordinates.put("height", height);
                coordinates.put("left", left);
                coordinates.put("top", top);
            } catch (IOException e) {
                System.err.println("解析裁剪数据时出错 (ParseCoord): " + e.getMessage());
                // 可以选择抛出异常或者返回空 Map
                return null;
            }
        }
        return coordinates;
    }
}