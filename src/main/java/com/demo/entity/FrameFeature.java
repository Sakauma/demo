package com.demo.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.Instant;
import java.nio.file.Path;

/**
 * 特征数据实体类
 * 数据库中的每一行代表一个已处理的帧及其所有特征。
 */
@Data
@Entity
@Table(name = "FALSEALARMITEMDATA")
public class FrameFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id; // 数据库主键

    /**
     * 标识符，用于将在同一次运行中生成的所有帧组合在一起
     * (例如: "feature2025-10-30-19-40-38")
     */
    @Column(nullable = false)
    private String analysisId;

    /**
     * 帧在分析批次中的索引（从0开始）
     */
    private int frameIndex;

    /**
     * 帧数据被保存到此数据库的时间
     */
    private Instant createdAt = Instant.now();

    // --- Category Info ---
    /**
     * 存储该帧的置信度数组，例如 "0.1,0.05,0.8"
     */
    @Lob // 对于可能很长的字符串使用 @Lob
    private String confidences;

    @Column(name = "FATIME")
    private Instant faTime;
    //@Lob // [!! 新增 !!] @Lob 告诉 JPA 这是一个大对象 (用于存储字节)
    //private byte[] rawData; // [!! 新增 !!] (用于步骤 5)

    @Transient // [!! 5. 确保使用 @Transient !!]
    private Path rawDataPath;

    public Path getRawDataPath() {
        return rawDataPath;
    }

    public void setRawDataPath(Path rawDataPath) {
        this.rawDataPath = rawDataPath;
    }

    // --- Numerical Feature ---
    private Float variance;
    private Float mean_region;
    private Float SCR;
    private Float contrast;
    private Float entropy;
    private Float homogeneity;
    private Float smoothness;
    private Float skewness;
    private Float kurtosis;
    private Float aspectRatio;
    private Float longAxis;
    private Float shortAxis;

    // --- XJY Related ---
    private Integer xjy_area;
    private Float peak_cell_intensity;
    private Float xjy_background_intensity;

    // --- Box ---
    private Integer tl_xs;
    private Integer tl_ys;
    private Integer widths;
    private Integer heights;

    // --- GJ Related ---
    private Integer peakPosX;
    private Integer peakPosY;
    private Float pixelVelocityX;
    private Float pixelVelocityY;

    // --- Radiance Related ---
    private Float apMaxRad;
    private Float apTotalRad;
    private Float apAvgRad;
    private Float brightnessTemperature;
    private Float brightnessTemperatureBG;

    // --- Geography Information ---
    private Float lgt;
    private Float lat;
    private Float alt;

    // --- Time Related ---
    private Short year;
    private Short month;
    private Short day;
    private Short hour;
    private Short min;
    private Short sec;
    private Float msec;
}