package com.demo.service.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.Pointer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

import com.demo.dto.MultiFrameResultResponse;
import com.demo.exception.ProcessException;
import com.demo.service.ConfigService;
import com.demo.dto.ConfigDto;

/**
 * @description 多帧图像处理服务，通过 JNA (Java Native Access) 与 C++ 核心库交互。
 * 这个类专门负责处理基于文件夹的多帧图像序列。它会：
 * 1. 扫描指定目录下的所有文件。
 * 2. 将文件路径列表传递给 C++ 库进行批量处理。
 * 3. 接收 C++ 库返回的处理结果，包括输出目录和状态信息。
 * 4. 依赖 {@link ConfigService} 来获取处理所需的配置（如裁剪区域）。
 * 5. 管理与 C++ 库交互的内存生命周期。
 */
@Service
public class MultiFrameProcessorCpp {
    // 静态初始化块，用于在类加载时配置 JNA 环境。
    static {
        // 根据操作系统设置 JNA 寻找原生库的路径
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MultiFrameProcessorCpp.class);
    private final ConfigService configService;

    /**
     * @description 构造函数，通过 Spring 的依赖注入初始化 ConfigService。
     * @param configService 配置服务，用于获取应用配置，如裁剪参数。
     */
    @Autowired
    public MultiFrameProcessorCpp(ConfigService configService) {
        this.configService = configService;
        logger.info("ConfigService 已注入到 MultiFrameProcessorCpp。");
    }

    /**
     * @description JNA 结构体，映射 C++ 中的 `CropBox` 结构体。
     * 用于在 Java 和 C++ 之间传递图像裁剪区域的坐标和尺寸。
     * @see ImgProcessorCpp.CropBox 这是一个共享的结构体定义，逻辑与单帧处理器中的相同。
     */
    public static class CropBox extends Structure {
        public int x;
        public int y;
        public int width;
        public int height;

        public CropBox() {
            super(ALIGN_DEFAULT); // 默认对齐方式
        }

        // 定义结构体字段的顺序，必须与 C++ 侧完全一致
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y", "width", "height");
        }

        /**
         * @description CropBox 的子类，实现了 Structure.ByValue 标记接口。
         * 这告诉 JNA 当此对象作为函数参数传递时，应按值传递整个结构体。
         */
        public static class ByValue extends CropBox implements Structure.ByValue {
            public ByValue(int x, int y, int width, int height) {
                this.x = x;
                this.y = y;
                this.width = width;
                this.height = height;
            }

            public ByValue() {
            }
        }
    }

    /**
     * @description JNA 结构体，映射 C++ 中用于接收输入数据的 `InputData` 结构体。
     */
    public static class InputData extends Structure {
        public String originalBase64; // 在多帧模式下，这是一个逗号分隔的文件绝对路径列表。
        public String croppedBase64;  // 在多帧模式下，通常与 originalBase64 相同。
        public String algorithmName;  // 使用的算法名称。
        public int fileNum;           // 要处理的文件总数。
        public int mode;              // 处理模式（例如，1 代表多帧）。
        public int imgType;           // 图像类型（未使用）。
        public int id;                // 请求ID（未使用）。
        public CropBox.ByValue crop;  // 裁剪框参数（按值传递）。

        public InputData() {
            super(ALIGN_DEFAULT);
        }

        // 定义字段顺序，必须与 C++ 侧一致
        protected List<String> getFieldOrder() {
            return Arrays.asList("originalBase64", "croppedBase64", "algorithmName", "fileNum", "mode", "imgType", "id", "crop");
        }

        /**
         * @description 实现了 Structure.ByReference 的内部类。
         * JNA 会传递指向该结构体实例的指针给 C++ 函数。
         */
        public static class ByReference extends InputData implements Structure.ByReference {
        }
    }

    /**
     * @description JNA 结构体，映射 C++ 中用于返回处理结果的 `OutputData` 结构体。
     */
    public static class OutputData extends Structure {
        public String processedBase64; // 在多帧模式下，此字段通常不被使用。
        public Pointer result;         // 指向由 C++ 分配的结果数据（可能是特征数组）的指针。
        public int result_length;      // 结果数据的长度。
        public String message;         // C++ 返回的状态或调试消息。
        public String outputDir;       // C++ 处理后生成所有结果文件的输出目录路径。
        public int fileNum;            // C++ 实际成功处理的文件数量。

        public OutputData() {
            super(ALIGN_DEFAULT);
        }

        // 定义字段顺序
        protected List<String> getFieldOrder() {
            return Arrays.asList("processedBase64", "result", "result_length", "message", "outputDir", "fileNum");
        }

        /**
         * @description 从 JNA 指针中安全地提取 float 数组。
         * @return 一个包含结果数据的 float 数组，如果指针为空或长度为0，则返回空数组。
         */
        public float[] getResultArray() {
            if (this.result_length <= 0 || this.result == null) {
                return new float[0];
            }
            try {
                return this.result.getFloatArray(0, this.result_length);
            } catch (Exception e) {
                logger.error("尝试从 OutputData.result 读取浮点数组时出错 (长度: {}): {}", this.result_length, e.getMessage());
                return new float[0];
            }
        }

        /**
         * @description 按引用传递的标记子类。
         */
        public static class ByReference extends OutputData implements Structure.ByReference {
        }
    }

    /**
     * @description JNA 接口，定义了要从多帧处理原生库中调用的函数。
     */
    public interface NativeMultiFrameLib extends Library {
        /**
         * 加载名为 "XJYTXFXCV_multi" 的 C++ 动态链接库。
         */
        NativeMultiFrameLib INSTANCE = Native.load("XJYTXFXCV_multi", NativeMultiFrameLib.class);

        /**
         * @description 映射 C++ 库中的 `processImageWrapper` 函数。
         * @param input C++函数期望接收的输入数据结构体（通过指针传递）。
         * @param output C++函数将填充的结果数据结构体（通过指针传递）。
         * @return 返回一个整型状态码，0 表示成功。
         */
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);

        /**
         * @description 映射 C++ 库中的 `freeOutputData` 函数，用于释放 C++ 分配的内存。
         * @param output 需要被释放内存的结构体。
         */
        void freeOutputData(OutputData.ByReference output);
    }

    // 被注释掉的代码块，原意可能是从.ini文件加载配置，现在已被ConfigService替代。
    // 保留注释可以帮助理解代码的演进历史。
    /*
    private CropBox.ByValue loadCropBoxFromIni() throws IOException { ... }
    */

    /**
     * @description 处理指定目录中的所有文件，执行多帧图像识别。
     * @param inputDirPath 包含图像文件的输入目录的绝对路径。
     * @param algorithmName 要使用的算法名称。
     * @return 返回一个 {@link MultiFrameResultResponse} 对象，其中包含处理结果的详细信息。
     * @throws IOException 如果读取目录或文件时发生 I/O 错误。
     * @throws ProcessException 如果处理过程中发生逻辑错误（如目录为空）或 C++ 库返回错误。
     */
    public MultiFrameResultResponse processDirectory(String inputDirPath, String algorithmName) throws IOException {
        logger.info("开始处理多帧图像: 目录 '{}', 算法: {}", inputDirPath, algorithmName);

        // --- 1. 扫描并收集目录下的所有文件路径 ---
        List<String> filePathsList;
        try (Stream<Path> paths = Files.list(Paths.get(inputDirPath))) {
            filePathsList = paths
                    .filter(Files::isRegularFile) // 只保留文件，忽略子目录
                    .map(Path::toAbsolutePath)    // 转换为绝对路径
                    .map(Path::toString)          // 转换为字符串
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("无法读取输入目录中的文件列表: {}", inputDirPath, e);
            throw new ProcessException("无法读取输入目录 '" + inputDirPath + "': " + e.getMessage(), e);
        }

        if (filePathsList.isEmpty()) {
            logger.warn("输入目录 {} 为空或不包含文件。", inputDirPath);
            throw new ProcessException("指定的输入目录不包含任何文件。");
        }

        // --- 2. 准备传递给 C++ 的数据 ---
        // 将文件路径列表合并成一个由逗号分隔的字符串
        String commaSeparatedFilePaths = String.join(",", filePathsList);
        int numFiles = filePathsList.size();
        logger.info("共有 {} 个图像文件。准备调用C++处理。", numFiles);
        // 打印部分路径用于调试，避免日志过长
        logger.debug("文件列表 (部分): {}", commaSeparatedFilePaths.substring(0, Math.min(200, commaSeparatedFilePaths.length())));

        // 从 ConfigService 加载裁剪框配置
        logger.info("正在从 ConfigService 加载配置...");
        ConfigDto config = configService.getConfig();
        ConfigDto.Region region = config.getRegion();
        CropBox.ByValue cropBoxConfig = new CropBox.ByValue(
                region.getX(), region.getY(), region.getWidth(), region.getHeight()
        );
        logger.info("从 ConfigService 成功加载裁剪框: x={}, y={}, width={}, height={}",
                region.getX(), region.getY(), region.getWidth(), region.getHeight());

        // --- 3. 调用 C++ 库 ---
        InputData.ByReference inputData = new InputData.ByReference();
        OutputData.ByReference outputData = new OutputData.ByReference();
        int processStatus = -1;

        try {
            // 填充输入结构体
            inputData.mode = 1; // 1 代表多帧模式
            inputData.algorithmName = algorithmName;
            inputData.originalBase64 = commaSeparatedFilePaths;
            inputData.croppedBase64 = commaSeparatedFilePaths;
            inputData.fileNum = numFiles;
            inputData.id = 0; // 未使用
            inputData.imgType = 0; // 未使用
            inputData.crop = cropBoxConfig;

            logger.info("调用C++ processImageWrapper (多帧模式)...");
            processStatus = NativeMultiFrameLib.INSTANCE.processImageWrapper(inputData, outputData);
            logger.info("C++ processImageWrapper (多帧模式) 返回状态: {}", processStatus);

            // --- 4. 处理 C++ 返回结果 ---
            if (processStatus == 0) { // 成功
                String resultOutputDir = outputData.outputDir;
                if (resultOutputDir == null || resultOutputDir.trim().isEmpty()) {
                    logger.error("C++ (多帧) 处理成功但未返回有效的输出目录路径。");
                    throw new ProcessException("核心算法处理成功但未指定输出目录。");
                }
                logger.info("C++ (多帧) 处理成功。消息: '{}', 输出目录: '{}'", outputData.message, resultOutputDir);

                // 根据原始文件名，推断出结果图像和ROI图像的文件名
                List<String> originalFileNamesOnly = filePathsList.stream()
                        .map(fullPath -> new File(fullPath).getName())
                        .collect(Collectors.toList());
                List<String> interestImageNames = new ArrayList<>();
                List<String> outputImageNames = new ArrayList<>();

                for (String originalNameWithExt : originalFileNamesOnly) {
                    // 假设结果文件名与原始文件名（除扩展名外）有固定关系
                    String baseName = originalNameWithExt.substring(0, originalNameWithExt.lastIndexOf('.'));
                    interestImageNames.add("roi_" + baseName + ".png");
                    outputImageNames.add(baseName + ".png");
                }

                MultiFrameResultResponse.ResultFiles resultFiles =
                        new MultiFrameResultResponse.ResultFiles(originalFileNamesOnly, interestImageNames, outputImageNames);

                // 构建并返回给 Controller 的最终响应对象
                return new MultiFrameResultResponse(
                        true,
                        resultOutputDir,
                        resultFiles,
                        outputData.message != null ? outputData.message : "处理成功",
                        outputData.fileNum // 使用 C++ 返回的实际处理文件数
                );

            } else { // 失败
                String errorMsg = "C++ (多帧) 处理失败。状态: " + processStatus;
                if (outputData.message != null && !outputData.message.isEmpty()) {
                    errorMsg += ", 消息: " + outputData.message;
                }
                logger.error(errorMsg);
                throw new ProcessException(errorMsg);
            }
        } catch (UnsatisfiedLinkError ule) {
            logger.error("JNA链接错误: {}", ule.getMessage(), ule);
            throw new ProcessException("无法链接到多帧核心处理库。确保 XJYTXFXCV_multi 及其依赖项正确。", ule);
        }
        finally {
            // --- 5. 内存管理 ---
            // 无论成功或失败，都必须调用 freeOutputData 释放 C++ 分配的内存
            if (outputData != null && outputData.getPointer() != null) {
                try {
                    NativeMultiFrameLib.INSTANCE.freeOutputData(outputData);
                    logger.info("已调用 freeOutputData (多帧) 清理 OutputData。");
                } catch (Exception e) {
                    logger.error("调用 freeOutputData (多帧) 时发生错误。", e);
                }
            }
        }
    }
}