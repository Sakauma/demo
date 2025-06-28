package com.demo.util;

import org.springframework.util.Base64Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * 提供图像裁剪功能的工具类。
 * 将 Base64 编码的图像按照指定的坐标进行裁剪，并返回裁剪后的 Base64 编码图像。
 */
public class CropImg {

    /**
     * 裁剪 Base64 编码的图像。
     *
     * @param base64Image    Base64 编码的原始图像字符串
     * @param cropCoordinates 裁剪坐标的 Map，包含以下键：
     *                       - "left": 裁剪区域的左边界坐标
     *                       - "top": 裁剪区域的上边界坐标
     *                       - "width": 裁剪区域的宽度
     *                       - "height": 裁剪区域的高度
     * @return 裁剪后的 Base64 编码图像字符串
     * @throws IOException 如果图像解码或编码过程中发生错误
     */
    public static String cropImage(String base64Image, Map<String, Integer> cropCoordinates) throws IOException {
        // 如果没有裁剪数据或输入为空，则直接返回原始图像
        if (base64Image == null || cropCoordinates == null || cropCoordinates.isEmpty()) {
            return base64Image;
        }

        // 将 Base64 字符串解码为字节数组
        byte[] imageBytes = Base64Utils.decodeFromString(base64Image);
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage originalImage = ImageIO.read(bais);

        // 从 Map 中获取裁剪坐标
        int x = cropCoordinates.get("left");
        int y = cropCoordinates.get("top");
        int width = cropCoordinates.get("width");
        int height = cropCoordinates.get("height");

        // 裁剪图像
        BufferedImage croppedImage = originalImage.getSubimage(x, y, width, height);

        // 将裁剪后的图像编码为 Base64 字符串
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(croppedImage, "png", baos);
        byte[] croppedImageBytes = baos.toByteArray();

        return Base64Utils.encodeToString(croppedImageBytes);
    }
}