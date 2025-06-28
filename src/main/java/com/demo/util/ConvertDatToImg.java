package com.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Base64Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;

/**
 * 数据转图像的工具类
 * 将 .dat 文件或字节数组转换为 PNG 图像，并返回 Base64 编码的字符串。
 */
public class ConvertDatToImg {
    private static final Logger logger = LoggerFactory.getLogger(ConvertDatToImg.class);

    /**
     * 将 .dat 文件或字节数组转换为 PNG 图像的 Base64 编码字符串。
     *
     * @param datBytes  输入的字节数组，可以是 .dat 文件数据或图像文件数据
     * @param filename  文件名，用于判断是否为 .dat 文件
     * @param rows      图像的行数（仅对 .dat 文件有效）
     * @param cols      图像的列数（仅对 .dat 文件有效）
     * @return          包含 Base64 编码字符串的 ConvertResult 对象，转换失败时返回 null
     */
    public static ConvertResult convertToPngBase64(byte[] datBytes, String filename, int rows, int cols) {
        try {
            String normalizedBase64 = null;
            if (filename.toLowerCase().endsWith(".dat")) {
                // 处理 .dat 文件
                if (rows <= 0 || cols <= 0) {
                    logger.error("无效的行列数。Rows: {}, Cols: {}", rows, cols);
                    return null;
                }

                // 使用 ByteBuffer 读取 .dat 文件数据
                ByteBuffer buffer = ByteBuffer.wrap(datBytes).order(ByteOrder.LITTLE_ENDIAN);
                DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();

                // 检查数据是否足够生成图像
                if (doubleBuffer.capacity() < rows * cols) {
                    logger.error(".dat 文件数据不足以生成图像。需要: {}, 可用: {}", (rows * cols), doubleBuffer.capacity());
                    return null;
                }

                // 读取像素数据
                double[] pixelDataDouble = new double[rows * cols];
                doubleBuffer.get(pixelDataDouble, 0, rows * cols);

                // 创建归一化图像
                BufferedImage normalizedImage = createNormalizedImage(pixelDataDouble, rows, cols);
                normalizedBase64 = encodeToBase64(normalizedImage);

                // saveImageToLocal(normalizedImage, filename); // 可选：保存图像到本地
            } else {
                // 处理非 .dat 文件（假设是图像文件）
                ByteArrayInputStream bais = new ByteArrayInputStream(datBytes);
                BufferedImage image = ImageIO.read(bais);
                if (image == null) {
                    logger.error("无法读取图像文件: {}", filename);
                    return null;
                }
                normalizedBase64 = encodeToBase64(image);
            }
            return new ConvertResult(normalizedBase64);

        } catch (IOException e) {
            logger.error("转换文件到 PNG 失败 (ConvertDatToImg): {}", e.getMessage(), e);
            return null;
        } catch (NegativeArraySizeException | BufferUnderflowException e) {
            logger.error("读取 .dat 文件数据时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据 double 数组创建归一化的灰度图像。
     *
     * @param pixelDataFloat 像素数据数组
     * @param imageRows      图像的行数
     * @param imageCols      图像的列数
     * @return               归一化后的 BufferedImage 对象
     */
    private static BufferedImage createNormalizedImage(double[] pixelDataFloat, int imageRows, int imageCols) {
        if (pixelDataFloat == null || pixelDataFloat.length < imageRows * imageCols) {
            logger.error("pixelDataFloat 为空或长度不足 {}x{}", imageRows, imageCols);
            return new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY); // 返回占位图像
        }

        // 计算数据的最小值和最大值
        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;
        for (int i = 0; i < imageRows * imageCols; i++) {
            double val = pixelDataFloat[i];
            minVal = Math.min(minVal, val);
            maxVal = Math.max(maxVal, val);
        }

        // 创建 RGB 图像
        BufferedImage bufferedImage = new BufferedImage(imageCols, imageRows, BufferedImage.TYPE_3BYTE_BGR);
        for (int row = 0; row < imageRows; row++) {
            for (int col = 0; col < imageCols; col++) {
                int pixelIndex = row * imageCols + col;

                // 归一化像素值到 [0, 1] 范围
                double normalizedVal = 0;
                if (maxVal > minVal) {
                    normalizedVal = (pixelDataFloat[pixelIndex] - minVal) / (maxVal - minVal);
                } else if (maxVal == minVal && minVal != 0) {
                    normalizedVal = 0.5; // 处理所有值相同的情况
                }

                // 转换为灰度值（0-255）
                int grayValue = (int) (normalizedVal * 255);
                grayValue = Math.min(255, Math.max(0, grayValue));
                int rgb = (grayValue << 16) | (grayValue << 8) | grayValue; // RGB 值相同（灰度）
                bufferedImage.setRGB(col, row, rgb);
            }
        }
        return bufferedImage;
    }

    /**
     * 将 BufferedImage 编码为 Base64 字符串。
     *
     * @param image 输入的 BufferedImage 对象
     * @return      Base64 编码的字符串
     * @throws IOException 如果图像写入失败
     */
    private static String encodeToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] pngImageBytes = baos.toByteArray();
        return Base64Utils.encodeToString(pngImageBytes);
    }

    /**
     * 将 BufferedImage 保存到本地文件。
     *
     * @param image     输入的 BufferedImage 对象
     * @param filename  原始文件名（用于生成输出文件名）
     * @throws IOException 如果文件保存失败
     */
    private static void saveImageToLocal(BufferedImage image, String filename) throws IOException {
        String localFilePath = "temp_images/" + filename.replace(".dat", ".png");
        File outputDir = new File("temp_images");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        ImageIO.write(image, "png", new File(localFilePath));
        logger.info("已将 .dat 文件转换为 PNG 并保存到: {}", localFilePath);
    }

    /**
     * 用于封装转换结果的内部类。
     */
    public static class ConvertResult {
        public String normalizedBase64; // Base64 编码的 PNG 图像字符串

        public ConvertResult(String normalizedBase64) {
            this.normalizedBase64 = normalizedBase64;
        }
    }
}