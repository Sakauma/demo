package com.utils;

import org.springframework.util.Base64Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

public class cropImg {

    public static String cropImage(String base64Image, Map<String, Integer> cropCoordinates) throws IOException {
        if (base64Image == null || cropCoordinates == null || cropCoordinates.isEmpty()) {
            return base64Image; // 如果没有裁剪数据，则返回原始图像
        }

        byte[] imageBytes = Base64Utils.decodeFromString(base64Image);
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        BufferedImage originalImage = ImageIO.read(bais);

        int x = cropCoordinates.get("left");
        int y = cropCoordinates.get("top");
        int width = cropCoordinates.get("width");
        int height = cropCoordinates.get("height");

        BufferedImage croppedImage = originalImage.getSubimage(x, y, width, height);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(croppedImage, "png", baos);
        byte[] croppedImageBytes = baos.toByteArray();

        return Base64Utils.encodeToString(croppedImageBytes);
    }
}