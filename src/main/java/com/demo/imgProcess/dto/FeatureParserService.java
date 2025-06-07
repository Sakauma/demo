package com.demo.imgProcess.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeatureParserService {
    private static final Logger logger = LoggerFactory.getLogger(FeatureParserService.class);

    private static final List<FeatureDefinition> ALL_FEATURE_DEFINITIONS_ORDERED = List.of(
            new FeatureDefinition("variance", 'f'),
            new FeatureDefinition("mean_region", 'f'),
            new FeatureDefinition("SCR", 'f'),
            new FeatureDefinition("contrast", 'f'),
            new FeatureDefinition("entropy", 'f'),
            new FeatureDefinition("homogeneity", 'f'),
            new FeatureDefinition("smoothness", 'f'),
            new FeatureDefinition("skewness", 'f'),
            new FeatureDefinition("kurtosis", 'f'),
            new FeatureDefinition("xjy_area", 'i'),         // 整型特征示例
            new FeatureDefinition("peak_cell_intensity", 'f'),
            new FeatureDefinition("xjy_background_intensity", 'f'),
            new FeatureDefinition("tl_xs", 'i'),
            new FeatureDefinition("tl_ys", 'i'),
            new FeatureDefinition("widths", 'i'),
            new FeatureDefinition("heights", 'i')
    );

    public Map<String, List<? extends Number>> parseFeatureFile(String filePath) throws IOException {
        Map<String, List<? extends Number>> allParsedFeatures = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             DataInputStream dis = new DataInputStream(fis)) {

            byte[] buffer4Bytes = new byte[4]; // 用于读取4字节的整数和浮点数

            // 1. 读取帧数 (num_frames)
            if (dis.read(buffer4Bytes) < 4) {
                throw new IOException("无法从文件读取 num_frames (文件过短): " + filePath);
            }
            int numFrames = ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            logger.info("特征文件 '{}' 包含 {} 帧的数据。", filePath, numFrames);

            if (numFrames <= 0) {
                logger.warn("特征文件 '{}' 中没有有效的数据帧 (numFrames = {})。将返回空的特征集。", filePath, numFrames);
                for (FeatureDefinition def : ALL_FEATURE_DEFINITIONS_ORDERED) {
                    if (def.getTypeChar() == 'f') {
                        allParsedFeatures.put(def.getName(), new ArrayList<Float>());
                    } else if (def.getTypeChar() == 'i') {
                        allParsedFeatures.put(def.getName(), new ArrayList<Integer>());
                    }
                }
                return allParsedFeatures;
            }

            for (FeatureDefinition featureDef : ALL_FEATURE_DEFINITIONS_ORDERED) {
                String featureName = featureDef.getName();
                char typeChar = featureDef.getTypeChar();
                int typeSize = featureDef.getTypeSize(); // 应为4

                if (typeChar == 'f') {
                    List<Float> values = new ArrayList<>(numFrames);
                    for (int i = 0; i < numFrames; i++) {
                        if (dis.read(buffer4Bytes) < typeSize) {
                            throw new IOException("读取特征 '" + featureName + "' (float) 的第 " + i + " 帧数据时文件意外结束。");
                        }
                        values.add(ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getFloat());
                    }
                    allParsedFeatures.put(featureName, values);
                } else if (typeChar == 'i') {
                    List<Integer> values = new ArrayList<>(numFrames);
                    for (int i = 0; i < numFrames; i++) {
                        if (dis.read(buffer4Bytes) < typeSize) {
                            throw new IOException("读取特征 '" + featureName + "' (int) 的第 " + i + " 帧数据时文件意外结束。");
                        }
                        values.add(ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getInt());
                    }
                    allParsedFeatures.put(featureName, values);
                } else {
                    logger.warn("特征 '{}' 定义了不支持的类型字符 '{}'。该特征将被跳过。", featureName, typeChar);
                }
            }
            logger.info("成功从 '{}' 解析了 {} 个特征 (共 {} 帧数据)。", filePath, allParsedFeatures.size(), numFrames);

        } catch (IOException e) {
            logger.error("解析特征文件 '{}' 时发生IO异常: {}", filePath, e.getMessage(), e);
            throw e;
        }
        return allParsedFeatures;
    }
}