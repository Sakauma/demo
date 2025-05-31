package com.demo.imgProcess;

import java.util.List;
import java.util.Arrays;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.ptr.FloatByReference;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ImgProcessorCpp {
    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
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
        String LIB_NAME = "XJYTXFXCV"; // C++ 库名
        ImageProcessingLibrary INSTANCE = (ImageProcessingLibrary) Native.load(LIB_NAME, ImageProcessingLibrary.class);

        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);
        void freeOutputData(OutputData.ByReference output);
    }

    public String processImage(String imgBase64, String cropBase64, Map<String, Integer> cropCoordinates, String algorithm) {
        OutputData output = processImageWithStruct(imgBase64, cropBase64, cropCoordinates, algorithm);
        return output.processedBase64;
    }

    public OutputData processImageWithStruct(String imgBase64, String cropBase64, Map<String, Integer> cropCoordinates, String algorithm) {
        OutputData.ByReference outputData = new OutputData.ByReference();
        InputData.ByReference inputData = new InputData.ByReference();
        int result = -1; // 初始化 result

        try {
            if (cropCoordinates != null && !cropCoordinates.isEmpty()) {
                int x = cropCoordinates.getOrDefault("x", 0);
                int y = cropCoordinates.getOrDefault("y", 0);
                int width = cropCoordinates.getOrDefault("width", 0);
                int height = cropCoordinates.getOrDefault("height", 0);
                inputData.crop = new CropBox.ByValue(x, y, width, height);
            }
            inputData.algorithmName = algorithm;
            inputData.originalBase64 = imgBase64;
            inputData.croppedBase64 = cropBase64;

            // 调用 C++ 函数
            ImageProcessingLibrary lib = ImageProcessingLibrary.INSTANCE;
            System.out.println("Java OutputData pointer before native call: " + outputData.getPointer());
            result = lib.processImageWrapper(inputData, outputData);
            System.out.println("Java OutputData pointer after native call: " + outputData.getPointer());

            System.out.println("C++ 返回值: " + result);

            if (result == 0) {
                // 返回值为 0，执行后面的打印操作
                System.out.println("C++ 处理后的消息: " + outputData.message);
                System.out.println("Result length: " + outputData.result_length);

                // 打印指针值
                //System.out.println("Java processedBase64 pointer: " + outputData.processedBase64);
                //System.out.println("Java result pointer: " + outputData.result);
                //System.out.println("Java message pointer: " + outputData.message);

                float[] resultArray = outputData.getResult();
                System.out.println("得到的 float 数组:");
                System.out.println(Arrays.toString(resultArray));
            } else {
                // 返回值不为 0，打印调用失败信息
                System.out.println("调用 C++ 图像处理失败，返回值为: " + result);
                System.out.println("C++ 错误消息: " + outputData.message); // 打印 C++ 的错误消息
            }
            return outputData;

        } finally {
            // 重要：释放 C++ 分配的内存
            // ImageProcessingLibrary lib = ImageProcessingLibrary.INSTANCE;
            //lib.freeOutputData(outputData);
        }
    }
}
