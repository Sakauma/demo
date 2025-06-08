package com.utils;

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


public class ConvertDatToImg {
    private static final Logger logger = LoggerFactory.getLogger(ConvertDatToImg.class);
    public static ConvertResult convertToPngBase64(byte[] datBytes, String filename, int rows, int cols) {
        try {
            String normalizedBase64 = null;
            if (filename.toLowerCase().endsWith(".dat")) {
                if (rows <= 0 || cols <= 0) {
                    logger.error("无效的行列数。Rows: {}, Cols: {}", rows, cols);
                    return null;
                }

                ByteBuffer buffer = ByteBuffer.wrap(datBytes).order(ByteOrder.LITTLE_ENDIAN);
                DoubleBuffer doubleBuffer = buffer.asDoubleBuffer();

                if (doubleBuffer.capacity() < rows * cols) { // Check if buffer has enough elements
                    logger.error(".dat 文件数据不足以生成图像。需要: {}, 可用: {}", (rows * cols), doubleBuffer.capacity());
                    return null;
                }
                double[] pixelDataDouble = new double[rows * cols];
                doubleBuffer.get(pixelDataDouble, 0, rows * cols);

                BufferedImage normalizedImage = createNormalizedImage(pixelDataDouble, rows, cols);
                normalizedBase64 = encodeToBase64(normalizedImage);

                // saveImageToLocal(normalizedImage, filename); // Optional
            } else {
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

    private static BufferedImage createNormalizedImage(double[] pixelDataFloat, int imageRows, int imageCols) {
        if (pixelDataFloat == null || pixelDataFloat.length < imageRows * imageCols) {
            logger.error("pixelDataFloat 为空或长度不足 {}x{}", imageRows, imageCols);
            return new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY); // Placeholder
        }

        double minVal = Double.MAX_VALUE;
        double maxVal = -Double.MAX_VALUE;
        for (int i = 0; i < imageRows * imageCols; i++) {
            double val = pixelDataFloat[i];
            minVal = Math.min(minVal, val);
            maxVal = Math.max(maxVal, val);
        }

        BufferedImage bufferedImage = new BufferedImage(imageCols, imageRows, BufferedImage.TYPE_3BYTE_BGR);
        for (int row = 0; row < imageRows; row++) {
            for (int col = 0; col < imageCols; col++) {
                int pixelIndex = row * imageCols + col;

                double normalizedVal = 0;
                if (maxVal > minVal) {
                    normalizedVal = (pixelDataFloat[pixelIndex] - minVal) / (maxVal - minVal);
                } else if (maxVal == minVal && minVal != 0) {
                    normalizedVal = 0.5;
                }

                int grayValue = (int) (normalizedVal * 255);
                grayValue = Math.min(255, Math.max(0, grayValue));
                int rgb = (grayValue << 16) | (grayValue << 8) | grayValue;
                bufferedImage.setRGB(col, row, rgb);
            }
        }
        return bufferedImage;
    }

    private static String encodeToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] pngImageBytes = baos.toByteArray();
        return Base64Utils.encodeToString(pngImageBytes);
    }

    private static void saveImageToLocal(BufferedImage image, String filename) throws IOException {
        String localFilePath = "temp_images/" + filename.replace(".dat", ".png");
        File outputDir = new File("temp_images");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        ImageIO.write(image, "png", new File(localFilePath));
        logger.info("已将 .dat 文件转换为 PNG 并保存到: {}", localFilePath);
    }

    public static class ConvertResult { //
        public String normalizedBase64; //
        public ConvertResult(String normalizedBase64) { //
            this.normalizedBase64 = normalizedBase64; //
        }
    }
}