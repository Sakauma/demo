package com.demo.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 解析裁剪坐标数据的工具类。
 * 该类提供静态方法，用于从JSON格式的字符串中提取裁剪坐标信息。
 */
public class ParseCoord {
    // 使用Jackson库的ObjectMapper实例，用于JSON解析
    private static final ObjectMapper objectMapper = new ObjectMapper();
    // 使用SLF4J日志记录器，记录解析过程中的错误信息
    private static final Logger logger = LoggerFactory.getLogger(ParseCoord.class);

    /**
     * 解析JSON格式的裁剪数据，提取坐标信息。
     *
     * @param cropDataJson 包含裁剪数据的JSON字符串，格式应包含width、height、left、top字段
     * @return 包含解析后的坐标信息的Map，键为"width"、"height"、"left"、"top"，值为对应的整数值
     *         如果输入为空或解析失败，返回null
     */
    public static Map<String, Integer> parse(String cropDataJson) {
        // 创建用于存储坐标信息的Map
        Map<String, Integer> coordinates = new HashMap<>();

        // 检查输入是否为空或空字符串
        if (cropDataJson != null && !cropDataJson.isEmpty()) {
            try {
                // 使用ObjectMapper将JSON字符串解析为JsonNode对象
                JsonNode cropData = objectMapper.readTree(cropDataJson);

                // 从JsonNode中提取各个坐标字段，并转换为整型
                int width = cropData.get("width").asInt();
                int height = cropData.get("height").asInt();
                int left = cropData.get("left").asInt();
                int top = cropData.get("top").asInt();

                // 将解析后的值存入Map中
                coordinates.put("width", width);
                coordinates.put("height", height);
                coordinates.put("left", left);
                coordinates.put("top", top);
            } catch (IOException e) {
                // 记录解析过程中发生的IO异常
                logger.error("解析裁剪数据时出错 (ParseCoord): {}", e.getMessage(), e);
                return null;
            }
        }
        return coordinates;
    }
}