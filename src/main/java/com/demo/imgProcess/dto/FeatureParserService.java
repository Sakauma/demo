package com.demo.imgProcess.dto;

import com.demo.config.FeatureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    // 定义一个 final 成员变量来保存从配置中读取的特征定义
    private final List<FeatureDefinition> featureDefinitions;

    // 通过构造函数注入 FeatureProperties
    @Autowired
    public FeatureParserService(FeatureProperties featureProperties) {
        this.featureDefinitions = featureProperties.getDefinitions();
        if (this.featureDefinitions == null || this.featureDefinitions.isEmpty()) {
            logger.warn("未能从配置文件加载任何特征定义！");
        }
    }

    public Map<String, List<? extends Number>> parseFeatureFile(String filePath) throws IOException {
        Map<String, List<? extends Number>> allParsedFeatures = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             DataInputStream dis = new DataInputStream(fis)) {

            byte[] buffer4Bytes = new byte[4];
            if (dis.read(buffer4Bytes) < 4) {
                throw new IOException("无法从文件读取 num_frames (文件过短): " + filePath);
            }
            int numFrames = ByteBuffer.wrap(buffer4Bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            logger.info("特征文件 '{}' 包含 {} 帧的数据。", filePath, numFrames);

            if (numFrames <= 0) {
                logger.warn("特征文件 '{}' 中没有有效的数据帧 (numFrames = {})。将返回空的特征集。", filePath, numFrames);
                for (FeatureDefinition def : this.featureDefinitions) { // 使用注入的列表
                    if (def.getTypeChar() == 'f') {
                        allParsedFeatures.put(def.getName(), new ArrayList<Float>());
                    } else if (def.getTypeChar() == 'i') {
                        allParsedFeatures.put(def.getName(), new ArrayList<Integer>());
                    }
                }
                return allParsedFeatures;
            }

            for (FeatureDefinition featureDef : this.featureDefinitions) {
                String featureName = featureDef.getName();
                char typeChar = featureDef.getTypeChar();
                int typeSize = featureDef.getTypeSize();

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