package com.demo.service;

import com.demo.entity.FrameFeature;
import com.demo.repository.FrameFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 负责将解析后的特征数据持久化到数据库和 SQL 文件。
 */
@Service
@RequiredArgsConstructor // 使用 Lombok 自动注入 final 字段
public class FeaturePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(FeaturePersistenceService.class);

    private final FrameFeatureRepository frameFeatureRepository;

    /**
     * 主入口方法。
     * 将特征Map转置为每帧的实体列表，然后保存到DB并转储到SQL文件。
     *
     * @param featureMap  从 FeatureParserService 获取的原始数据
     * @param analysisId  唯一的批次ID (例如 "feature2025-10-30-19-40-38")
     * @throws IOException 如果写入 SQL 文件失败
     */
    @Transactional // 确保 saveAll 在一个事务中完成
    public void persistFeatures(Map<String, List<? extends Number>> featureMap, String analysisId) throws IOException {

        // 1. 将 Map<String, List> "转置" 为 List<FrameFeature>
        List<FrameFeature> frames = transposeMapToFrames(featureMap, analysisId);

        if (frames.isEmpty()) {
            logger.warn("特征图谱转置后为空，跳过持久化。 (AnalysisID: {})", analysisId);
            return;
        }

        // 2. 批量保存到数据库 (JPA)
        logger.info("正在将 {} 帧数据批量保存到数据库... (AnalysisID: {})", frames.size(), analysisId);
        frameFeatureRepository.saveAll(frames);
        logger.info("数据库批量保存成功。");

        // 3. 生成 SQL 插入语句
        List<String> sqlInserts = new ArrayList<>();
        sqlInserts.add("BEGIN TRANSACTION;");
        for (FrameFeature frame : frames) {
            sqlInserts.add(generateInsertSql(frame));
        }
        sqlInserts.add("COMMIT;");

        // 4. 将 SQL 写入磁盘文件
        Path basePath = getApplicationBasePath();
        Path resultPath = basePath.resolve("result");
        Files.createDirectories(resultPath); // 确保 'result' 目录存在

        // SQL 文件将与 feature.dat 目录同名, 但后缀为 .sql
        Path sqlFilePath = resultPath.resolve(analysisId + ".sql");

        Files.write(sqlFilePath, sqlInserts, StandardCharsets.UTF_8);
        logger.info("成功将 SQL 插入语句落盘到: {}", sqlFilePath.toAbsolutePath());
    }

    /**
     * 辅助方法：将 Map 转置为实体列表。
     */
    @SuppressWarnings("unchecked") // 我们知道 featureMap 的结构
    private List<FrameFeature> transposeMapToFrames(Map<String, List<? extends Number>> featureMap, String analysisId) {

        // 从 Map 中安全地获取数据列表
        List<Integer> numFramesList = (List<Integer>) (List<?>) featureMap.get("numFrames");
        if (numFramesList == null || numFramesList.isEmpty()) {
            return new ArrayList<>();
        }
        int numFrames = numFramesList.get(0);
        int categoryNum = ((List<Integer>) (List<?>) featureMap.get("category_num")).get(0);

        List<Float> confidencesFlat = (List<Float>) (List<?>) featureMap.get("confidences");

        // 按特征名称获取列表
        Map<String, List<? extends Number>> data = featureMap;

        List<FrameFeature> frames = new ArrayList<>(numFrames);

        for (int i = 0; i < numFrames; i++) {
            FrameFeature frame = new FrameFeature();
            frame.setAnalysisId(analysisId);
            frame.setFrameIndex(i);

            // 提取该帧的置信度
            int start = i * categoryNum;
            int end = start + categoryNum;
            if (confidencesFlat != null && !confidencesFlat.isEmpty() && end <= confidencesFlat.size()) {
                List<Float> frameConfidences = confidencesFlat.subList(start, end);
                frame.setConfidences(frameConfidences.stream().map(Object::toString).collect(Collectors.joining(",")));
            }

            // --- 自动从 Map 填充所有字段 ---
            // (注意：getNumber 可处理 Float, Integer, Short)
            frame.setVariance(getNumber(data, "variance", i, Float.class));
            frame.setMean_region(getNumber(data, "mean_region", i, Float.class));
            frame.setSCR(getNumber(data, "SCR", i, Float.class));
            frame.setContrast(getNumber(data, "contrast", i, Float.class));
            frame.setEntropy(getNumber(data, "entropy", i, Float.class));
            frame.setHomogeneity(getNumber(data, "homogeneity", i, Float.class));
            frame.setSmoothness(getNumber(data, "smoothness", i, Float.class));
            frame.setSkewness(getNumber(data, "skewness", i, Float.class));
            frame.setKurtosis(getNumber(data, "kurtosis", i, Float.class));
            frame.setAspectRatio(getNumber(data, "aspectRatio", i, Float.class));
            frame.setLongAxis(getNumber(data, "longAxis", i, Float.class));
            frame.setShortAxis(getNumber(data, "shortAxis", i, Float.class));

            frame.setXjy_area(getNumber(data, "xjy_area", i, Integer.class));
            frame.setPeak_cell_intensity(getNumber(data, "peak_cell_intensity", i, Float.class));
            frame.setXjy_background_intensity(getNumber(data, "xjy_background_intensity", i, Float.class));

            frame.setTl_xs(getNumber(data, "tl_xs", i, Integer.class));
            frame.setTl_ys(getNumber(data, "tl_ys", i, Integer.class));
            frame.setWidths(getNumber(data, "widths", i, Integer.class));
            frame.setHeights(getNumber(data, "heights", i, Integer.class));

            frame.setPeakPosX(getNumber(data, "peakPosX", i, Integer.class));
            frame.setPeakPosY(getNumber(data, "peakPosY", i, Integer.class));
            frame.setPixelVelocityX(getNumber(data, "pixelVelocityX", i, Float.class));
            frame.setPixelVelocityY(getNumber(data, "pixelVelocityY", i, Float.class));

            frame.setApMaxRad(getNumber(data, "apMaxRad", i, Float.class));
            frame.setApTotalRad(getNumber(data, "apTotalRad", i, Float.class));
            frame.setApAvgRad(getNumber(data, "apAvgRad", i, Float.class));
            frame.setBrightnessTemperature(getNumber(data, "brightnessTemperature", i, Float.class));
            frame.setBrightnessTemperatureBG(getNumber(data, "brightnessTemperatureBG", i, Float.class));

            frame.setLgt(getNumber(data, "lgt", i, Float.class));
            frame.setLat(getNumber(data, "lat", i, Float.class));
            frame.setAlt(getNumber(data, "alt", i, Float.class));

            frame.setYear(getNumber(data, "year", i, Short.class));
            frame.setMonth(getNumber(data, "month", i, Short.class));
            frame.setDay(getNumber(data, "day", i, Short.class));
            frame.setHour(getNumber(data, "hour", i, Short.class));
            frame.setMin(getNumber(data, "min", i, Short.class));
            frame.setSec(getNumber(data, "sec", i, Short.class));
            frame.setMsec(getNumber(data, "msec", i, Float.class));

            frames.add(frame);
        }
        return frames;
    }

    /**
     * 安全地从 Map 中获取并转换每帧的数据
     */
    private <T extends Number> T getNumber(Map<String, List<? extends Number>> data, String key, int index, Class<T> type) {
        List<? extends Number> list = data.get(key);
        if (list == null || index >= list.size()) {
            return null;
        }
        Number value = list.get(index);

        // 根据需要的类型进行转换
        if (type == Float.class) return type.cast(value.floatValue());
        if (type == Integer.class) return type.cast(value.intValue());
        if (type == Short.class) return type.cast(value.shortValue());
        if (type == Double.class) return type.cast(value.doubleValue());
        return type.cast(value);
    }


    /**
     * 辅助方法：为 FrameFeature 实体生成 INSERT SQL 语句。
     */
    private String generateInsertSql(FrameFeature frame) {
        // 使用 StringBuilder 提高性能
        StringBuilder cols = new StringBuilder();
        StringBuilder vals = new StringBuilder();

        // 辅助方法，用于添加字段和值，并处理 null
        BiConsumer<String, Object> addField = (colName, value) -> {
            if (value != null) {
                cols.append(colName).append(", ");
                if (value instanceof String) {
                    // 处理字符串中的单引号
                    String escapedValue = value.toString().replace("'", "''");
                    vals.append("'").append(escapedValue).append("', ");
                } else {
                    vals.append(value).append(", ");
                }
            }
        };

        // 添加所有字段
        addField.accept("analysis_id", frame.getAnalysisId());
        addField.accept("frame_index", frame.getFrameIndex());
        addField.accept("created_at", frame.getCreatedAt() != null ? "'" + frame.getCreatedAt().toString() + "'" : null); // 特殊处理时间戳
        addField.accept("confidences", frame.getConfidences());

        addField.accept("variance", frame.getVariance());
        addField.accept("mean_region", frame.getMean_region());
        addField.accept("scr", frame.getSCR());
        addField.accept("contrast", frame.getContrast());
        addField.accept("entropy", frame.getEntropy());
        addField.accept("homogeneity", frame.getHomogeneity());
        addField.accept("smoothness", frame.getSmoothness());
        addField.accept("skewness", frame.getSkewness());
        addField.accept("kurtosis", frame.getKurtosis());
        addField.accept("aspect_ratio", frame.getAspectRatio());
        addField.accept("long_axis", frame.getLongAxis());
        addField.accept("short_axis", frame.getShortAxis());

        addField.accept("xjy_area", frame.getXjy_area());
        addField.accept("peak_cell_intensity", frame.getPeak_cell_intensity());
        addField.accept("xjy_background_intensity", frame.getXjy_background_intensity());

        addField.accept("tl_xs", frame.getTl_xs());
        addField.accept("tl_ys", frame.getTl_ys());
        addField.accept("widths", frame.getWidths());
        addField.accept("heights", frame.getHeights());

        addField.accept("peak_pos_x", frame.getPeakPosX());
        addField.accept("peak_pos_y", frame.getPeakPosY());
        addField.accept("pixel_velocity_x", frame.getPixelVelocityX());
        addField.accept("pixel_velocity_y", frame.getPixelVelocityY());

        addField.accept("ap_max_rad", frame.getApMaxRad());
        addField.accept("ap_total_rad", frame.getApTotalRad());
        addField.accept("ap_avg_rad", frame.getApAvgRad());
        addField.accept("brightness_temperature", frame.getBrightnessTemperature());
        addField.accept("brightness_temperaturebg", frame.getBrightnessTemperatureBG());

        addField.accept("lgt", frame.getLgt());
        addField.accept("lat", frame.getLat());
        addField.accept("alt", frame.getAlt());

        addField.accept("year", frame.getYear());
        addField.accept("month", frame.getMonth());
        addField.accept("day", frame.getDay());
        addField.accept("hour", frame.getHour());
        addField.accept("min", frame.getMin());
        addField.accept("sec", frame.getSec());
        addField.accept("msec", frame.getMsec());

        // 移除末尾的 ", "
        String finalCols = cols.substring(0, cols.length() - 2);
        String finalVals = vals.substring(0, vals.length() - 2);

        // 注意：表名 "frame_feature" 是 JPA 自动生成的，
        // 匹配 @Table(name = "frame_feature")
        return String.format("INSERT INTO frame_feature (%s) VALUES (%s);", finalCols, finalVals);
    }

    // 从 ConfigService 复制 getApplicationBasePath 逻辑
    //
    private Path getApplicationBasePath() {
        try {
            URL location = FeaturePersistenceService.class.getProtectionDomain().getCodeSource().getLocation();
            Path basePath;

            if ("jar".equals(location.getProtocol())) {
                String jarPathString = location.toURI().getSchemeSpecificPart();
                int bangIndex = jarPathString.indexOf('!');
                if (bangIndex != -1) {
                    jarPathString = jarPathString.substring(0, bangIndex);
                }
                Path jarFile = Paths.get(new URI(jarPathString));
                basePath = jarFile.getParent();
            } else { // "file" protocol for IDE
                Path classesPath = Paths.get(location.toURI());
                basePath = classesPath.getParent().getParent();
            }
            return basePath;
        } catch (Exception e) {
            logger.error("无法动态确定应用基准目录，将回退到使用当前工作目录。", e);
            return Paths.get(".");
        }
    }

    // Java 11+ 的 BiConsumer
    @FunctionalInterface
    interface BiConsumer<T, U> {
        void accept(T t, U u);
    }
}