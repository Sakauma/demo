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

import java.util.Map;

@Service
public class ImgProcessorCpp {
    private static final Logger logger = LoggerFactory.getLogger(ImgProcessorCpp.class);

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
    }

    // 仿照多帧模式的dto
    public static class SingleFrameResult {
        private final String processedBase64;
        private final float[] resultArray;
        private final int resultLength;
        private final String message;
        private final boolean success;

        public SingleFrameResult(boolean success, String processedBase64, float[] resultArray, int resultLength, String message) {
            this.success = success;
            this.processedBase64 = processedBase64;
            this.resultArray = resultArray;
            this.resultLength = resultLength;
            this.message = message;
        }

        // Add getters for all fields
        public String getProcessedBase64() { return processedBase64; }
        public float[] getResultArray() { return resultArray; }
        public int getResultLength() { return resultLength; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }


    // 定义裁剪框结构体
    public static class CropBox extends Structure {
        public int x;
        public int y;
        public int width;
        public int height;

        public CropBox() {
            super(ALIGN_DEFAULT);
        }

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

    // 定义输入数据的结构体
    public static class InputData extends Structure {
        public static class ByReference extends InputData implements Structure.ByReference {}

        public String originalBase64;
        public String croppedBase64;
        public String algorithmName;
        public CropBox.ByValue crop;

        public InputData() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("originalBase64", "croppedBase64", "algorithmName", "crop");
        }
    }

    // 定义输出数据的结构体，输出数据可以随时添加
    public static class OutputData extends Structure {
        public static class ByReference extends OutputData implements Structure.ByReference {}

        public String processedBase64;
        public FloatByReference result; // 指向结果1数组的指针
        public int result_length;     // 数组的长度
        public String message;

        public OutputData() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("processedBase64", "result", "result_length", "message");
        }

        public float[] getResult() {
            if (result == null || result_length <= 0) {
                return new float[0];
            }
            return result.getPointer().getFloatArray(0, result_length);
        }
    }

    public interface ImageProcessingLibrary extends Library {
        ImageProcessingLibrary INSTANCE = (ImageProcessingLibrary) Native.load("XJYTXFXCV", ImageProcessingLibrary.class);
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);
        void freeOutputData(OutputData.ByReference output);
    }

    public SingleFrameResult processImage(String imgBase64, String cropBase64, Map<String, Integer> cropCoordinates, String algorithm) {
        logger.info("开始处理单帧图像, 算法: {}", algorithm);

        OutputData.ByReference outputData = new OutputData.ByReference();
        InputData.ByReference inputData = new InputData.ByReference();
        int processStatus = -1;

        try {
            if (cropCoordinates != null && !cropCoordinates.isEmpty()) {
                inputData.crop = new CropBox.ByValue(
                        cropCoordinates.getOrDefault("x", 0),
                        cropCoordinates.getOrDefault("y", 0),
                        cropCoordinates.getOrDefault("width", 0),
                        cropCoordinates.getOrDefault("height", 0)
                );
            }
            inputData.algorithmName = algorithm;
            inputData.originalBase64 = imgBase64;
            inputData.croppedBase64 = cropBase64;

            logger.info("调用C++ processImageWrapper (单帧模式)...");
            processStatus = ImageProcessingLibrary.INSTANCE.processImageWrapper(inputData, outputData);
            logger.info("C++ processImageWrapper (单帧模式) 返回状态: {}", processStatus);

            if (processStatus == 0) {
                logger.info("C++ (单帧) 处理成功。消息: '{}', 结果长度: {}", outputData.message, outputData.result_length);
                logger.debug("得到的 float 数组: {}", Arrays.toString(outputData.getResult()));

                // 3. 在释放内存前，将数据复制到 DTO 中
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
                // 4. 处理失败时，也返回一个包含失败信息的 DTO
                return new SingleFrameResult(false, null, null, 0, errorMsg);
            }
        } catch (UnsatisfiedLinkError ule) {
            logger.error("JNA链接错误: {}", ule.getMessage(), ule);
            String errorMsg = "无法链接到单帧核心处理库。确保 XJYTXFXCV 及其依赖项正确。";
            return new SingleFrameResult(false, null, null, 0, errorMsg);
        } finally {
            if (outputData != null && outputData.getPointer() != null) {
                try {
                    ImageProcessingLibrary.INSTANCE.freeOutputData(outputData);
                    logger.info("已调用 freeOutputData (单帧) 清理 OutputData。");
                } catch (Exception e) {
                    logger.error("调用 freeOutputData (单帧) 时发生错误。", e);
                }
            }
        }
    }
}



