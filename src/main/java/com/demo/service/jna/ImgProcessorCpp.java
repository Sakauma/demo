package com.demo.service.jna;

import java.util.List;
import java.util.Arrays;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.ptr.FloatByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.demo.exception.ProcessException;

import java.util.Map;

@Service
public class ImgProcessorCpp {
    // 日志记录器，用于记录运行时信息
    private static final Logger logger = LoggerFactory.getLogger(ImgProcessorCpp.class);

    static {
        // 初始化时设置JNA库路径，根据操作系统动态调整
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
    }

    // 定义单帧处理结果的DTO（Data Transfer Object）
    public static class SingleFrameResult {
        private final String processedBase64;  // 处理后的图像Base64编码
        private final float[] resultArray;     // 处理结果数组
        private final int resultLength;        // 结果数组长度
        private final String message;          // 处理消息
        private final boolean success;         // 处理是否成功

        public SingleFrameResult(boolean success, String processedBase64, float[] resultArray, int resultLength, String message) {
            this.success = success;
            this.processedBase64 = processedBase64;
            this.resultArray = resultArray;
            this.resultLength = resultLength;
            this.message = message;
        }

        // 为所有字段提供getter方法
        public String getProcessedBase64() { return processedBase64; }
        public float[] getResultArray() { return resultArray; }
        public int getResultLength() { return resultLength; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }

    // 定义裁剪框结构体，用于传递裁剪坐标信息
    public static class CropBox extends Structure {
        public int x;
        public int y;
        public int width;
        public int height;

        public CropBox() {
            super(ALIGN_DEFAULT);
        }

        // 用于按值传递的结构体子类
        public static class ByValue extends CropBox implements Structure.ByValue {
            public ByValue(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
            public ByValue() {}

            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("x", "y", "width", "height");
            }
        }
    }

    // 定义输入数据的结构体，用于传递给C++库
    public static class InputData extends Structure {
        // 用于按引用传递的结构体子类
        public static class ByReference extends InputData implements Structure.ByReference {}

        public String originalBase64;  // 原始图像Base64编码
        public String croppedBase64;   // 裁剪后图像Base64编码
        public String algorithmName;   // 使用的算法名称
        public CropBox.ByValue crop;   // 裁剪框信息

        public InputData() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("originalBase64", "croppedBase64", "algorithmName", "crop");
        }
    }

    // 定义输出数据的结构体，用于接收C++库的处理结果
    public static class OutputData extends Structure {
        // 用于按引用传递的结构体子类
        public static class ByReference extends OutputData implements Structure.ByReference {}

        public String processedBase64;  // 处理后的图像Base64编码
        public FloatByReference result; // 指向结果数组的指针
        public int result_length;       // 结果数组长度
        public String message;          // 处理消息

        public OutputData() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("processedBase64", "result", "result_length", "message");
        }

        // 获取结果数组的方法
        public float[] getResult() {
            if (result == null || result_length <= 0) {
                return new float[0];
            }
            return result.getPointer().getFloatArray(0, result_length);
        }
    }

    // 定义C++库的接口
    public interface ImageProcessingLibrary extends Library {
        // 加载C++库并获取实例
        ImageProcessingLibrary INSTANCE = (ImageProcessingLibrary) Native.load("XJYTXFXCV", ImageProcessingLibrary.class);

        // 调用C++库的图像处理函数
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);

        // 释放C++库分配的输出数据内存
        void freeOutputData(OutputData.ByReference output);
    }

    // 处理单帧图像的主方法
    public SingleFrameResult processImage(String imgBase64, String cropBase64, Map<String, Integer> cropCoordinates, String algorithm) {
        logger.info("开始处理单帧图像, 算法: {}", algorithm);

        OutputData.ByReference outputData = new OutputData.ByReference();
        InputData.ByReference inputData = new InputData.ByReference();
        int processStatus = -1;

        try {
            // 设置裁剪框坐标
            if (cropCoordinates != null && !cropCoordinates.isEmpty()) {
                inputData.crop = new CropBox.ByValue(
                        cropCoordinates.getOrDefault("x", 0),
                        cropCoordinates.getOrDefault("y", 0),
                        cropCoordinates.getOrDefault("width", 0),
                        cropCoordinates.getOrDefault("height", 0)
                );
            }

            // 设置输入数据
            inputData.algorithmName = algorithm;
            inputData.originalBase64 = imgBase64;
            inputData.croppedBase64 = cropBase64;

            logger.info("调用C++ processImageWrapper (单帧模式)...");
            processStatus = ImageProcessingLibrary.INSTANCE.processImageWrapper(inputData, outputData);
            logger.info("C++ processImageWrapper (单帧模式) 返回状态: {}", processStatus);

            if (processStatus == 0) {
                logger.info("C++ (单帧) 处理成功。消息: '{}', 结果长度: {}", outputData.message, outputData.result_length);
                logger.debug("得到的 float 数组: {}", Arrays.toString(outputData.getResult()));

                // 在释放内存前，将数据复制到DTO中
                return new SingleFrameResult(
                        true,
                        outputData.processedBase64,
                        outputData.getResult(),
                        outputData.result_length,
                        outputData.message
                );
            } else {
                String errorMsg = String.format("C++ (单帧) 处理失败。状态: %d, 消息: %s", processStatus, outputData.message);
                logger.error(errorMsg);
                throw new ProcessException(errorMsg);
            }
        } catch (UnsatisfiedLinkError ule) {
            String errorMsg = "无法链接到单帧核心处理库。确保 XJYTXFXCV 及其依赖项正确。";
            logger.error("JNA链接错误: {}", ule.getMessage(), ule);
            throw new ProcessException(errorMsg, ule);
        } finally {
            // 确保释放C++库分配的内存
            if (outputData != null && outputData.getPointer() != null) {
                try {
                    ImageProcessingLibrary.INSTANCE.freeOutputData(outputData);
                    logger.info("已调用 freeOutputData (单帧) 清理 OutputData。");
                } catch (ProcessException e) {
                    logger.error("调用 freeOutputData (单帧) 时发生错误。", e);
                }
            }
        }
    }
}
