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
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
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
    public void persistFeatures(Map<String, List<? extends Number>> featureMap,
                                String analysisId,
                                List<Path> orderedRawFilePaths) throws IOException {

        // 1. 将 Map<String, List> "转置" 为 List<FrameFeature>
        List<FrameFeature> frames = transposeMapToFrames(featureMap, analysisId, orderedRawFilePaths);

        if (frames.isEmpty()) {
            logger.warn("特征图谱转置后为空，跳过持久化。 (AnalysisID: {})", analysisId);
            return;
        }

        // 2. 批量保存到数据库 (JPA)
        logger.info("正在将 {} 帧数据批量保存到数据库... (AnalysisID: {})", frames.size(), analysisId);
        frameFeatureRepository.saveAll(frames);
        logger.info("数据库批量保存成功。");

        // 3. 将 SQL 写入磁盘文件
        Path basePath = getApplicationBasePath();
        Path resultPath = basePath.resolve("result");
        Files.createDirectories(resultPath); // 确保 'result' 目录存在

        List<String> jpaSqlInserts = new ArrayList<>();
        jpaSqlInserts.add("BEGIN TRANSACTION;");
        for (FrameFeature frame : frames) {
            jpaSqlInserts.add(generateInsertSql(frame)); // (这个是旧的SQL生成方法)
        }
        jpaSqlInserts.add("COMMIT;");

        Path jpaSqlFilePath = resultPath.resolve(analysisId + "_db_import.sql");
        Files.write(jpaSqlFilePath, jpaSqlInserts, StandardCharsets.UTF_8);
        logger.info("成功将 [JPA导入SQL] 落盘到: {}", jpaSqlFilePath.toAbsolutePath());

        // 4. B. [!! 流式 !!] 生成用户要求的 "所有帧数据内容" (包含 BLOB) 的 SQL 文件
        Path frameDataSqlFilePath = resultPath.resolve(analysisId + "_frame_data.sql");
        logger.info("开始流式写入 [所有帧数据SQL] 到: {}", frameDataSqlFilePath.toAbsolutePath());

        // 我们使用带缓冲的 Writer 来逐行写入，而不是在内存中构建 List<String>
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(frameDataSqlFilePath.toFile()), StandardCharsets.UTF_8))) {

            writer.write("BEGIN TRANSACTION;\n"); // [!! 写入 !!]

            // 遍历所有帧
            for (FrameFeature frame : frames) {
                // [!! 关键 !!] 我们不再调用 generateFrameDataSql(frame)
                // 而是调用新的流式写入方法
                writeFrameDataSqlStreaming(writer, frame);
            }

            writer.write("COMMIT;\n"); // [!! 写入 !!]

        }
        logger.info("成功流式写入 [所有帧数据SQL]。");

        try {
            logger.info("开始计算和追加统计数据...");
            // 调用我们即将创建的新方法
            generateAndAppendStatisticsSql(frames, resultPath);
            logger.info("成功追加统计数据。");
        } catch (Exception e) {
            // 关键：统计失败不应该导致整个持久化过程失败
            logger.error("生成统计数据SQL文件时发生严重错误: {}", e.getMessage(), e);
        }
    }

//    /**
//     * [!! 新增 !!]
//     * 计算所有帧的统计数据，并将其作为一行 INSERT 语句
//     * 追加到固定的 SQL 文件中。
//     *
//     * @param frames     已转置的所有帧的数据列表
//     * @param resultPath 'result' 目录的路径
//     */
//    private void generateAndAppendStatisticsSql(List<FrameFeature> frames, Path resultPath) throws IOException {
//
//        // --- 0. 定义 SQL 格式 ---
//        final String TABLE_NAME = "FRAME_STATISTICS"; // [!! 请你确认这个表名 !!]
//        final String FILE_NAME = "statistics.sql";    // 固定的文件名
//
//        // 辅助工具，用于格式化 SQL 值
//        java.util.function.Function<Object, String> formatValue = (value) -> {
//            if (value == null) return "NULL";
//            if (value instanceof String || value instanceof Instant) {
//                return "'" + value.toString().replace("'", "''") + "'";
//            }
//            return value.toString();
//        };
//
//        // --- 1. 执行计算 ---
//        if (frames == null || frames.isEmpty()) {
//            logger.warn("帧列表为空，跳过统计数据生成。");
//            return;
//        }
//
//        FrameFeature firstFrame = frames.get(0);
//        FrameFeature lastFrame = frames.get(frames.size() - 1);
//
//        // 1. 时间差 (秒)
//        long timeDelta = 0;
//        if (firstFrame.getFaTime() != null && lastFrame.getFaTime() != null) {
//            timeDelta = Duration.between(firstFrame.getFaTime(), lastFrame.getFaTime()).toSeconds();
//        }
//
//        // 2. 经度差
//        Double lgtDelta = Double.valueOf((firstFrame.getLgt() != null && lastFrame.getLgt() != null)
//                ? (lastFrame.getLgt() - firstFrame.getLgt()) : null);
//
//        // 3. 纬度差
//        Double latDelta = Double.valueOf((firstFrame.getLat() != null && lastFrame.getLat() != null)
//                ? (lastFrame.getLat() - firstFrame.getLat()) : null);
//
//        // 4. 总帧数
//        int totalFrames = frames.size();
//
//        // 5. xjy_area 平均值
//        double avgXjyArea = frames.stream()
//                .map(FrameFeature::getXjy_area)
//                .filter(Objects::nonNull)
//                .mapToDouble(Integer::doubleValue)
//                .average()
//                .orElse(0.0);
//
//        // 6. mean_region 平均值
//        double avgMeanRegion = frames.stream()
//                .map(FrameFeature::getMean_region)
//                .filter(Objects::nonNull)
//                .mapToDouble(Float::doubleValue)   // 这里隐式拆箱
//                .average()
//                .orElse(0.0);                      // 空集合时直接给 0
//
//// 7~9. apAvgRad 统计
//        List<Float> apAvgRadValues = frames.stream()
//                .map(FrameFeature::getApAvgRad)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toList());
//
//        double avgApAvgRad   = 0.0;
//        double medianApAvgRad = 0.0;
//        double varianceApAvgRad = 0.0;
//
//        if (!apAvgRadValues.isEmpty()) {          // 先判空
//            // 7. 平均值
//            avgApAvgRad = apAvgRadValues.stream()
//                    .mapToDouble(Float::doubleValue)
//                    .average()
//                    .orElse(0.0);
//
//            // 8. 中位数
//            Collections.sort(apAvgRadValues);
//            int mid = apAvgRadValues.size() / 2;
//            medianApAvgRad = apAvgRadValues.size() % 2 == 0
//                    ? (apAvgRadValues.get(mid - 1) + apAvgRadValues.get(mid)) / 2.0
//                    : apAvgRadValues.get(mid);
//
//            // 9. 方差
//            final double mean = avgApAvgRad;
//            varianceApAvgRad = apAvgRadValues.stream()
//                    .mapToDouble(Float::doubleValue)
//                    .map(x -> (x - mean) * (x - mean))
//                    .average()
//                    .orElse(0.0);
//        }
//
//        // --- 2. 构建 SQL 语句 ---
//
//        String columns = String.join(", ",
//                "TIME_DELTA_SEC",
//                "LGT_DELTA",
//                "LAT_DELTA",
//                "TOTAL_FRAMES",
//                "AVG_XJY_AREA",
//                "AVG_MEAN_REGION",
//                "AVG_APAVGRAD",
//                "MEDIAN_APAVGRAD",
//                "VARIANCE_APAVGRAD"
//        );
//
//        String values = String.join(", ",
//                formatValue.apply(timeDelta),          // 1.
//                formatValue.apply(lgtDelta),           // 2.
//                formatValue.apply(latDelta),           // 3.
//                formatValue.apply(totalFrames),        // 4.
//                formatValue.apply(avgXjyArea),         // 5.
//                formatValue.apply(avgMeanRegion),      // 6.
//                formatValue.apply(avgApAvgRad),        // 7.
//                formatValue.apply(medianApAvgRad),     // 8.
//                formatValue.apply(varianceApAvgRad)    // 9.
//        );
//
//        String sqlInsertLine = String.format("INSERT INTO %s (%s) VALUES (%s);\n", TABLE_NAME, columns, values);
//
//        // --- 3. [!! 关键 !!] 追加写入文件 ---
//        Path sqlFilePath = resultPath.resolve(FILE_NAME);
//
//        // 使用 try-with-resources 和 FileOutputStream(..., true) 来追加
//        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
//                new FileOutputStream(sqlFilePath.toFile(), true), StandardCharsets.UTF_8))) {
//
//            // 检查文件是否是空的，如果是，先写入表头 (可选，但推荐)
//            if (Files.size(sqlFilePath) == 0) {
//                writer.write("-- 统计数据SQL文件 (固定文件名，自动追加)\n");
//                writer.write("BEGIN TRANSACTION;\n");
//                // 你可能还想在这里 CREATE TABLE IF NOT EXISTS ...
//            }
//
//            writer.write(sqlInsertLine);
//            // (我们不在这里写 COMMIT，假设用户会批量执行)
//        }
//    }

private void generateAndAppendStatisticsSql(List<FrameFeature> frames, Path resultPath) throws IOException {

    final String TABLE_NAME = "FRAME_STATISTICS";
    final String FILE_NAME  = "statistics.sql";

    java.util.function.Function<Object, String> formatValue = (v) -> {
        if (v == null) return "NULL";
        if (v instanceof String || v instanceof Instant)
            return "'" + v.toString().replace("'", "''") + "'";
        return v.toString();
    };

    /* ---------- 1. 空列表直接返回 ---------- */
    if (frames == null || frames.isEmpty()) {
        logger.warn("帧列表为空，跳过统计数据生成。");
        return;
    }

    FrameFeature first = frames.get(0);
    FrameFeature last  = frames.get(frames.size() - 1);

    /* ---------- 2. 时间差 ---------- */
    long timeDelta = 0;
    if (first.getFaTime() != null && last.getFaTime() != null) {
        timeDelta = Duration.between(first.getFaTime(), last.getFaTime()).getSeconds();
    }

    /* ---------- 3. 经纬度差 ---------- */
    Double lgtDelta = Double.valueOf((first.getLgt() != null && last.getLgt() != null)
            ? last.getLgt() - first.getLgt() : null);
    Double latDelta = Double.valueOf((first.getLat() != null && last.getLat() != null)
            ? last.getLat() - first.getLat() : null);

    int totalFrames = frames.size();

    /* ---------- 4. 平均值 / 中位数 / 方差 ---------- */
    double avgXjyArea = frames.stream()
            .map(FrameFeature::getXjy_area)
            .filter(Objects::nonNull)
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0.0);

    double avgMeanRegion = frames.stream()
            .map(FrameFeature::getMean_region)
            .filter(Objects::nonNull)
            .mapToDouble(Float::doubleValue)
            .average()
            .orElse(0.0);

    /* ---------- 5. apAvgRad 相关 ---------- */
    List<Float> apAvgRadList = frames.stream()
            .map(FrameFeature::getApAvgRad)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    double avgApAvgRad    = 0.0;
    double medianApAvgRad = 0.0;
    double varianceApAvgRad = 0.0;

    if (!apAvgRadList.isEmpty()) {
        avgApAvgRad = apAvgRadList.stream()
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        Collections.sort(apAvgRadList);
        int mid = apAvgRadList.size() / 2;
        medianApAvgRad = apAvgRadList.size() % 2 == 0
                ? (apAvgRadList.get(mid - 1) + apAvgRadList.get(mid)) / 2.0
                : apAvgRadList.get(mid);

        final double mean = avgApAvgRad;
        varianceApAvgRad = apAvgRadList.stream()
                .mapToDouble(Float::doubleValue)
                .map(x -> (x - mean) * (x - mean))
                .average()
                .orElse(0.0);
    }

    /* ---------- 6. 拼 SQL ---------- */
    String columns = "TIME_DELTA_SEC, LGT_DELTA, LAT_DELTA, TOTAL_FRAMES, " +
            "AVG_XJY_AREA, AVG_MEAN_REGION, AVG_APAVGRAD, MEDIAN_APAVGRAD, VARIANCE_APAVGRAD";

    String values = String.join(", ",
            formatValue.apply(timeDelta),
            formatValue.apply(lgtDelta),
            formatValue.apply(latDelta),
            formatValue.apply(totalFrames),
            formatValue.apply(avgXjyArea),
            formatValue.apply(avgMeanRegion),
            formatValue.apply(avgApAvgRad),
            formatValue.apply(medianApAvgRad),
            formatValue.apply(varianceApAvgRad)
    );

    String sql = String.format("INSERT INTO %s (%s) VALUES (%s);\n", TABLE_NAME, columns, values);

    /* ---------- 7. 追加写文件 ---------- */
    Path sqlFile = resultPath.resolve(FILE_NAME);
    try (Writer w = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(sqlFile.toFile(), true), StandardCharsets.UTF_8))) {

        if (Files.size(sqlFile) == 0) {
            w.write("-- 统计数据SQL文件 (固定文件名，自动追加)\n");
            w.write("BEGIN TRANSACTION;\n");
        }
        w.write(sql);
    }
}



    /**
     * 辅助方法：将 Map 转置为实体列表。
     */
    @SuppressWarnings("unchecked")
    private List<FrameFeature> transposeMapToFrames(Map<String, List<? extends Number>> featureMap,
                                                    String analysisId,
                                                    List<Path> orderedRawFilePaths) {
        List<? extends Number> referenceFeatureList = featureMap.get("variance");
        if (referenceFeatureList == null || referenceFeatureList.isEmpty()) {
            return new ArrayList<>();
        }
        int numFrames = referenceFeatureList.size();

        // 现在 categoryNum 也会出同样的问题，你需要一个更好的方法来获取它
        // 暂时假设 categoryNum 也是从某个地方(比如 "confidences")推断出来的
        List<Float> confidencesFlat = (List<Float>) (List<?>) featureMap.get("confidences");
        int categoryNum = 0;
        if (confidencesFlat != null && numFrames > 0) {
            categoryNum = confidencesFlat.size() / numFrames; // [!!] 动态推断 categoryNum
        } else if (confidencesFlat != null) {
            // numFrames 为 0，但 confidencesFlat 不为空，这是一种边缘情况
            categoryNum = confidencesFlat.size();
        }

        if (orderedRawFilePaths.size() != numFrames) {
            logger.error("持久化错误：特征帧数 ({}) 与原始文件数 ({}) 不匹配！", numFrames, orderedRawFilePaths.size());
        }

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

            Short year = getNumber(data, "year", i, Short.class);
            Short month = getNumber(data, "month", i, Short.class);
            Short day = getNumber(data, "day", i, Short.class);
            Short hour = getNumber(data, "hour", i, Short.class);
            Short min = getNumber(data, "min", i, Short.class);
            Short sec = getNumber(data, "sec", i, Short.class);
            Float msec = getNumber(data, "msec", i, Float.class);

            if (year != null && month != null && day != null && hour != null && min != null && sec != null && msec != null) {
                try {
                    // 将时间分量合并为 UTC Instant
                    ZonedDateTime zdt = ZonedDateTime.of(
                            year, month, day, hour, min, sec, (int)(msec * 1000000),
                            ZoneId.of("UTC") // 数据是UTC时间
                    );
                    // 假设 FrameFeature 实体中有一个 'private Instant faTime;' 字段
                    frame.setFaTime(zdt.toInstant());
                } catch (Exception e) {
                    logger.warn("在第 {} 帧解析时间戳失败: {}", i, e.getMessage());
                    frame.setFaTime(null);
                }
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

//            if (i < orderedRawFilePaths.size()) {
//                Path rawFilePath = orderedRawFilePaths.get(i);
//                byte[] rawData = readRawData(rawFilePath);
//                frame.setRawData(rawData);
//            }
            if (i < orderedRawFilePaths.size()) {
                frame.setRawDataPath(orderedRawFilePaths.get(i));
            }

            frames.add(frame);
        }
        return frames;
    }

    /**
     * [!! 新增 !!]
     * 辅助方法：将单个 INSERT 语句流式写入 Writer。
     * @param writer Writer (BufferedWriter)
     * @param frame 包含特征和原始文件路径的对象
     */
    private void writeFrameDataSqlStreaming(Writer writer, FrameFeature frame) throws IOException {
        final String TABLE_NAME = "FALSEALARMITEMDATA";

        // 辅助工具，用于格式化非 BLOB 数据
        java.util.function.Function<Object, String> formatValue = (value) -> {
            if (value == null) return "NULL";
            if (value instanceof String || value instanceof Instant) {
                return "'" + value.toString().replace("'", "''") + "'";
            }
            // (我们不再在这里处理 byte[])
            return value.toString();
        };

        // 1. 写入 SQL 语句的前半部分
        writer.write(String.format("INSERT INTO %s (%s) VALUES (",
                TABLE_NAME,
                String.join(", ", // Columns (与之前相同)
                        "FATIME", "FRAME_INDEX", "PEAKPOSX", "PEAKPOSY", "PIXELVELOCITYX",
                        "PIXELVELOCITYY", "XJY_AREA", "LONGAXIS", "SHORTAXIS", "ALLINTENSITY",
                        "PEAK_CELL_INTENSITY", "MEAN_REGION", "APTOTALRAD", "APMAXRAD",
                        "APAVGRAD", "BRIGHTNESSTEMPERATURE", "BRIGHTNESSTEMPERATUREBG",
                        "SCR", "LGT", "LAT", "LHT", "DIMENSION", "RAW_IMAGE_DATA"
                )
        ));

        // 2. 写入所有非 BLOB 的值
        writer.write(String.join(", ",
                formatValue.apply(frame.getFaTime()),         // 1.
                formatValue.apply(frame.getFrameIndex()),     // 2.
                formatValue.apply(frame.getPeakPosX()),       // 3.
                formatValue.apply(frame.getPeakPosY()),       // 4.
                formatValue.apply(frame.getPixelVelocityX()), // 5.
                formatValue.apply(frame.getPixelVelocityY()), // 6.
                formatValue.apply(frame.getXjy_area()),       // 7.
                formatValue.apply(frame.getLongAxis()),       // 8.
                formatValue.apply(frame.getShortAxis()),      // 9.
                "1024",                                       // 10.
                formatValue.apply(frame.getPeak_cell_intensity()), // 11.
                formatValue.apply(frame.getMean_region()),    // 12.
                formatValue.apply(frame.getApTotalRad()),     // 13.
                formatValue.apply(frame.getApMaxRad()),       // 14.
                formatValue.apply(frame.getApAvgRad()),       // 15.
                formatValue.apply(frame.getBrightnessTemperature()), // 16.
                formatValue.apply(frame.getBrightnessTemperatureBG()), // 17.
                formatValue.apply(frame.getSCR()),            // 18.
                formatValue.apply(frame.getLgt()),            // 19.
                formatValue.apply(frame.getLat()),            // 20.
                "0",                                          // 21.
                "32"                                          // 22.
        ));

        // 3. [!! 流式写入 BLOB !!]
        writer.write(", "); // 写入 BLOB 值之前的逗号

        Path rawDataPath = frame.getRawDataPath();
        if (rawDataPath == null || !Files.exists(rawDataPath)) {
            writer.write("NULL"); // 原始文件路径不存在
        } else {
            // 调用流式 Hex 转换器
            streamHex(writer, rawDataPath);
        }

        // 4. 写入 SQL 语句的结尾
        writer.write(");\n"); // 分号和换行
    }

    /**
     * [!! 已优化 !!]
     * 核心流式处理方法：读取一个文件，并将其内容作为 Hex 流写入 Writer。
     * 使用更大的缓冲区并批量写入字符，以提高I/O性能。
     * @param writer Writer (BufferedWriter)
     * @param filePath 原始 .dat 文件的路径
     */
    private void streamHex(Writer writer, Path filePath) throws IOException {
        // 用于将 byte 转换为 hex 字符
        final char[] hexChars = "0123456789ABCDEF".toCharArray();

        writer.write("X'"); // 1. 写入 SQLite BLOB 起始符

        // [!! 优化 1 !!]
        // 将缓冲区大小从 8KB 增加到 64KB (64 * 1024 = 65536)
        // 你可以根据服务器内存进一步调大，例如 1024 * 1024 (1MB)
        final int BUFFER_SIZE = 65536;

        // [!! 优化 2 !!]
        // 创建一个字节缓冲区和
        // 一个字符缓冲区 (大小是字节缓冲区的两倍，因为 1 byte = 2 hex chars)
        byte[] byteBuffer = new byte[BUFFER_SIZE];
        char[] hexBuffer = new char[BUFFER_SIZE * 2];

        // 2. 使用 try-with-resources 打开文件输入流
        try (InputStream in = new FileInputStream(filePath.toFile())) {
            int bytesRead;

            // 3. 循环读取文件
            while ((bytesRead = in.read(byteBuffer, 0, BUFFER_SIZE)) != -1) {

                // 4. [!! 核心 !!]
                // 在内存中将字节批量转换为十六进制字符
                for (int i = 0; i < bytesRead; i++) {
                    int v = byteBuffer[i] & 0xFF;
                    hexBuffer[i * 2] = hexChars[v >>> 4];     // 高4位
                    hexBuffer[i * 2 + 1] = hexChars[v & 0x0F]; // 低4位
                }

                // 5. [!! 优化 3 !!]
                // 一次性批量写入转换后的所有字符
                writer.write(hexBuffer, 0, bytesRead * 2);
            }
        }

        writer.write("'"); // 6. 写入 SQLite BLOB 结束符
    }

//    /**
//     * 安全地从 Map 中获取并转换每帧的数据
//     */
//    private <T extends Number> T getNumber(Map<String, List<? extends Number>> data, String key, int index, Class<T> type) {
//        List<? extends Number> list = data.get(key);
//        if (list == null || index >= list.size()) {
//            return null;
//        }
//        Number value = list.get(index);
//
//        // 根据需要的类型进行转换
//        if (type == Float.class) return type.cast(value.floatValue());
//        if (type == Integer.class) return type.cast(value.intValue());
//        if (type == Short.class) return type.cast(value.shortValue());
//        if (type == Double.class) return type.cast(value.doubleValue());
//        return type.cast(value);
//    }
    /**
     * [!! 已修复 !!]
     * 安全地从 Map 中获取并转换每帧的数据
     */
    private <T extends Number> T getNumber(Map<String, List<? extends Number>> data, String key, int index, Class<T> type) {
        List<? extends Number> list = data.get(key);
        if (list == null || index >= list.size()) {
            return null;
        }
        Number value = list.get(index);

        // [!! 修复 !!] 增加对 value 本身的 null 检查
        if (value == null) {
            return null;
        }

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