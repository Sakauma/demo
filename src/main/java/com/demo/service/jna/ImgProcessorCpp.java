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

/**
 * @description 单帧图像处理服务，通过 JNA (Java Native Access) 调用底层 C++ 核心库。
 * 这个类是 Java 和 C++ 图像处理算法之间的桥梁。它负责：
 * 1. 定义与 C++ 库中数据结构相匹配的 Java `Structure` 类。
 * 2. 加载 C++ 动态链接库 (DLL/SO)。
 * 3. 封装调用 C++ 函数的逻辑，包括数据准备、函数调用和结果解析。
 * 4. 确保 C++ 分配的内存被正确释放，防止内存泄漏。
 */
@Service
public class ImgProcessorCpp {
    // 日志记录器，用于记录运行时信息、警告和错误。
    private static final Logger logger = LoggerFactory.getLogger(ImgProcessorCpp.class);

    // 静态初始化块，在类加载时执行。
    static {
        // 初始化时设置 JNA 寻找原生库的路径。
        // 这使得 Native.load() 可以在指定目录中找到动态库文件。
        // 这里根据操作系统动态调整，以增强跨平台兼容性。
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
        // 对于 Linux 或 macOS，可以添加相应的 else if 分支来设置路径。
    }

    /**
     * @description 用于封装单帧处理结果的数据传输对象 (DTO)。
     * 这个类的实例是不可变的，在构造后只提供 getter 方法，保证了数据的稳定性。
     */
    public static class SingleFrameResult {
        private final String processedBase64;  // 处理后用于显示的图像的Base64编码字符串。
        private final float[] resultArray;     // 算法返回的特征数据数组。
        private final int resultLength;        // 特征数据数组的长度。
        private final String message;          // 来自C++库的状态或调试消息。
        private final boolean success;         // 标记处理是否成功。

        public SingleFrameResult(boolean success, String processedBase64, float[] resultArray, int resultLength, String message) {
            this.success = success;
            this.processedBase64 = processedBase64;
            this.resultArray = resultArray;
            this.resultLength = resultLength;
            this.message = message;
        }

        // --- Getters ---
        public String getProcessedBase64() { return processedBase64; }
        public float[] getResultArray() { return resultArray; }
        public int getResultLength() { return resultLength; }
        public String getMessage() { return message; }
        public boolean isSuccess() { return success; }
    }

    /**
     * @description JNA 结构体，映射 C++ 中的 `CropBox` 结构体。
     * 用于在 Java 和 C++ 之间传递图像裁剪区域的坐标和尺寸。
     */
    public static class CropBox extends Structure {
        public int x;
        public int y;
        public int width;
        public int height;

        public CropBox() {
            super(ALIGN_DEFAULT); // 使用 JNA 默认的内存对齐方式
        }

        /**
         * @description CropBox 的子类，实现了 Structure.ByValue 标记接口。
         * 这告诉 JNA 当此对象作为函数参数传递时，应传递整个结构体的值（而不是指向它的指针）。
         * 这对于需要按值传递结构体的 C++ 函数至关重要。
         */
        public static class ByValue extends CropBox implements Structure.ByValue {
            public ByValue(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }
            public ByValue() {}

            /**
             * 定义结构体中字段的顺序，必须与 C++ 中定义的顺序完全一致，以确保内存布局正确。
             */
            @Override
            protected List<String> getFieldOrder() {
                return Arrays.asList("x", "y", "width", "height");
            }
        }
    }

    /**
     * @description JNA 结构体，映射 C++ 中用于接收输入数据的 `InputData` 结构体。
     */
    public static class InputData extends Structure {
        /**
         * 实现了 Structure.ByReference 的内部类。
         * 当此类的实例作为函数参数时，JNA 会传递指向该结构体内存的指针。
         * 这是向 C/C++ 函数传递大型结构体或期望函数修改其内容时的标准做法。
         */
        public static class ByReference extends InputData implements Structure.ByReference {}

        public String originalBase64;  // 原始图像的Base64编码。
        public String croppedBase64;   // 裁剪后图像的Base64编码。
        public String algorithmName;   // 请求使用的算法名称。
        public CropBox.ByValue crop;   // 裁剪框信息（按值传递）。

        public InputData() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("originalBase64", "croppedBase64", "algorithmName", "crop");
        }
    }

    /**
     * @description JNA 结构体，映射 C++ 中用于返回处理结果的 `OutputData` 结构体。
     * C++ 函数会填充这个结构体的实例。
     */
    public static class OutputData extends Structure {
        public static class ByReference extends OutputData implements Structure.ByReference {}

        public String processedBase64;  // C++处理后生成的图像的Base64编码。
        /**
         * @description 指向一个由 C++ 分配的 float 数组的指针。
         * 使用 FloatByReference 类型让 JNA 知道这是一个指向浮点数的指针。
         */
        public FloatByReference result;
        public int result_length;       // 上述 float 数组的长度。
        public String message;          // C++返回的状态或错误消息。

        public OutputData() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("processedBase64", "result", "result_length", "message");
        }

        /**
         * @description 从 JNA 指针中安全地提取 float 数组。
         * @return 一个包含结果数据的 float 数组，如果指针为空或长度为0，则返回空数组。
         */
        public float[] getResult() {
            if (result == null || result_length <= 0) {
                return new float[0];
            }
            // getPointer() 获取原始指针，然后 getFloatArray() 从该指针开始读取指定数量的浮点数。
            return result.getPointer().getFloatArray(0, result_length);
        }
    }

    /**
     * @description JNA 接口，定义了要从原生库中调用的函数。
     * JNA 会自动代理这个接口
     */
    public interface ImageProcessingLibrary extends Library {
        /**
         * @description 加载指定的 C++ 动态链接库，并创建接口的实例。
         * "XJYTXFXCV" 是库的名称（在Windows上会自动寻找 XJYTXFXCV.dll，在Linux上是 libXJYTXFXCV.so）。
         */
        ImageProcessingLibrary INSTANCE = (ImageProcessingLibrary) Native.load("XJYTXFXCV", ImageProcessingLibrary.class);

        /**
         * @description 映射 C++ 库中的 `processImageWrapper` 函数。
         * @param input C++函数期望接收的输入数据结构体（通过指针传递）。
         * @param output C++函数将填充的结果数据结构体（通过指针传递）。
         * @return 返回一个整型状态码，通常 0 表示成功，非 0 表示失败。
         */
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);

        /**
         * @description 映射 C++ 库中的 `freeOutputData` 函数。
         * 这个函数用于释放由 C++ 在 `processImageWrapper` 调用期间动态分配的内存
         * @param output 需要被释放内存的结构体。
         */
        void freeOutputData(OutputData.ByReference output);
    }

    /**
     * @description 处理单帧图像的主业务方法。
     * @param imgBase64 原始图像的 Base64 字符串。
     * @param cropBase64 裁剪后图像的 Base64 字符串。
     * @param cropCoordinates 包含裁剪坐标的 Map。
     * @param algorithm 要使用的算法名称。
     * @return 返回一个包含处理结果的 SingleFrameResult 对象。
     * @throws ProcessException 如果 C++ 库返回错误或发生 JNA 链接错误。
     */
    public SingleFrameResult processImage(String imgBase64, String cropBase64, Map<String, Integer> cropCoordinates, String algorithm) {
        logger.info("开始处理单帧图像, 算法: {}", algorithm);

        // 准备传递给 C++ 的输入和输出结构体
        OutputData.ByReference outputData = new OutputData.ByReference();
        InputData.ByReference inputData = new InputData.ByReference();
        int processStatus = -1; // 初始化处理状态

        try {
            // 如果提供了裁剪坐标，则填充 CropBox 结构体
            if (cropCoordinates != null && !cropCoordinates.isEmpty()) {
                inputData.crop = new CropBox.ByValue(
                        cropCoordinates.getOrDefault("x", 0),
                        cropCoordinates.getOrDefault("y", 0),
                        cropCoordinates.getOrDefault("width", 0),
                        cropCoordinates.getOrDefault("height", 0)
                );
            }

            // 填充输入数据结构体
            inputData.algorithmName = algorithm;
            inputData.originalBase64 = imgBase64;
            inputData.croppedBase64 = cropBase64;

            // 调用 C++ 核心处理函数
            logger.info("调用C++ processImageWrapper (单帧模式)...");
            processStatus = ImageProcessingLibrary.INSTANCE.processImageWrapper(inputData, outputData);
            logger.info("C++ processImageWrapper (单帧模式) 返回状态: {}", processStatus);

            // 根据返回状态码进行处理
            if (processStatus == 0) { // 0 代表成功
                logger.info("C++ (单帧) 处理成功。消息: '{}', 结果长度: {}", outputData.message, outputData.result_length);
                logger.debug("得到的 float 数组: {}", Arrays.toString(outputData.getResult()));

                // 在 finally 块释放内存之前，将需要的数据从 JNA 结构体复制到安全的 Java DTO 对象中。
                return new SingleFrameResult(
                        true,
                        outputData.processedBase64,
                        outputData.getResult(),
                        outputData.result_length,
                        outputData.message
                );
            } else { // 非 0 代表失败
                String errorMsg = String.format("C++ (单帧) 处理失败。状态: %d, 消息: %s", processStatus, outputData.message);
                logger.error(errorMsg);
                throw new ProcessException(errorMsg); // 抛出自定义异常，由全局异常处理器捕获
            }
        } catch (UnsatisfiedLinkError ule) {
            // 捕获 JNA 无法找到或链接到库的错误
            String errorMsg = "无法链接到单帧核心处理库。确保 XJYTXFXCV 及其依赖项正确。";
            logger.error("JNA链接错误: {}", ule.getMessage(), ule);
            throw new ProcessException(errorMsg, ule);
        } finally {
            // 无论成功还是失败，都必须尝试释放 C++ 分配的内存。
            if (outputData != null && outputData.getPointer() != null) {
                try {
                    // 调用 C++ 的内存释放函数
                    ImageProcessingLibrary.INSTANCE.freeOutputData(outputData);
                    logger.info("已调用 freeOutputData (单帧) 清理 OutputData。");
                } catch (ProcessException e) {
                    // 记录释放内存时可能发生的错误，但不向上抛出，以免覆盖原始异常。
                    logger.error("调用 freeOutputData (单帧) 时发生错误。", e);
                }
            }
        }
    }
}