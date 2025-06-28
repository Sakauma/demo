package com.demo.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ParseCoord {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(ParseCoord.class);
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
                logger.error("解析裁剪数据时出错 (ParseCoord): {}", e.getMessage(), e);
                return null;
            }
        }
        return coordinates;
    }
}