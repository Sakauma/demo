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
import org.springframework.beans.factory.annotation.Value;

import com.demo.dto.MultiFrameResultResponse;
import com.demo.exception.ProcessException;
import com.demo.service.ConfigService;
import com.demo.dto.ConfigDto;

/**
 * 多帧处理服务类
 * 使用 JNA（Java Native Access）与 C++ 库交互，处理多帧图像
 */
@Service
public class MultiFrameProcessorCpp {
    static {
        // 根据操作系统设置 JNA 库路径
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MultiFrameProcessorCpp.class);
    private final ConfigService configService;

    @Autowired
    public MultiFrameProcessorCpp(ConfigService configService) {
        this.configService = configService;
        logger.info("ConfigService 已注入到 MultiFrameProcessorCpp。");
    }

    /**
     * 定义裁剪框结构体，用于指定图像裁剪区域
     */
    public static class CropBox extends Structure {
        public int x;
        public int y;
        public int width;
        public int height;

        public CropBox() {
            super(ALIGN_DEFAULT); // 默认对齐方式
        }

        // 定义结构体字段的顺序
        protected List<String> getFieldOrder() {
            return Arrays.asList("x", "y", "width", "height");
        }

        // 定义按值传递的裁剪框结构体
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
     * 定义输入数据结构体，包含处理多帧图像所需的所有参数
     */
    public static class InputData extends Structure {
        public String originalBase64; // 原始图像路径的 base64 编码
        public String croppedBase64; // 裁剪后图像路径的 base64 编码
        public String algorithmName; // 使用的算法名称
        public int fileNum; // 文件数量
        public int mode; // 处理模式
        public int imgType; // 图像类型
        public int id; // 请求 ID
        public CropBox.ByValue crop; // 裁剪框参数

        public InputData() {
            super(ALIGN_DEFAULT); // 默认对齐方式
        }

        // 定义结构体字段的顺序
        protected List<String> getFieldOrder() {
            return Arrays.asList("originalBase64", "croppedBase64", "algorithmName", "fileNum", "mode", "imgType", "id", "crop");
        }

        // 定义按引用传递的输入数据结构体
        public static class ByReference extends InputData implements Structure.ByReference {
        }
    }

    /**
     * 定义输出数据结构体，包含处理结果和相关信息
     */
    public static class OutputData extends Structure {
        public String processedBase64; // 处理后的图像路径的 base64 编码
        public Pointer result; // 结果数据指针
        public int result_length; // 结果数据长度
        public String message; // 处理消息
        public String outputDir; // 输出目录
        public int fileNum; // 文件数量

        public OutputData() {
            super(ALIGN_DEFAULT); // 默认对齐方式
        }

        // 定义结构体字段的顺序
        protected List<String> getFieldOrder() {
            return Arrays.asList("processedBase64", "result", "result_length", "message", "outputDir", "fileNum");
        }

        // 获取结果数据数组
        public float[] getResultArray() {
            if (this.result_length <= 0 || this.result == null) {
                return new float[0];
            }
            try {
                return this.result.getFloatArray(0, this.result_length);
            } catch (ProcessException e) {
                logger.error("尝试从 OutputData.result 读取浮点数组时出错 (长度: {}): {}", this.result_length, e.getMessage());
                return new float[0];
            }
        }

        // 定义按引用传递的输出数据结构体
        public static class ByReference extends OutputData implements Structure.ByReference {
        }
    }

    /**
     * 定义本地库接口，声明需要调用的 C++ 函数
     */
    public interface NativeMultiFrameLib extends Library {
        NativeMultiFrameLib INSTANCE = Native.load("XJYTXFXCV_multi", NativeMultiFrameLib.class);

        // 声明 C++ 库中的 processImageWrapper 函数
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);

        // 声明 C++ 库中的 freeOutputData 函数
        void freeOutputData(OutputData.ByReference output);
    }

//    private CropBox.ByValue loadCropBoxFromIni() throws IOException {
//        File iniFile = new File(this.iniFilePath);
//
//        if (!iniFile.exists() || !iniFile.isFile()) {
//            String errorMessage = String.format("配置文件 '%s' 未找到或不是一个有效文件。", iniFilePath);
//            logger.error(errorMessage);
//            throw new IOException(errorMessage);
//        }
//
//        try (FileReader reader = new FileReader(iniFile)) {
//            Ini ini = new Ini();
//            ini.load(reader);
//            Ini.Section regionSection = ini.get("Region");
//
//            if (regionSection == null) {
//                String errorMessage = String.format("配置文件 '%s' 中必需的 '[Region]' 部分未找到。", iniFilePath);
//                logger.error(errorMessage);
//                throw new IOException(errorMessage);
//            }
//
//            int x = regionSection.get("x", int.class);
//            int y = regionSection.get("y", int.class);
//            int width = regionSection.get("width", int.class);
//            int height = regionSection.get("height", int.class);
//
//            logger.info("从 INI 文件 '{}' 的 '[Region]' 部分成功加载裁剪框: x={}, y={}, width={}, height={}",
//                    iniFilePath, x, y, width, height);
//            return new CropBox.ByValue(x, y, width, height);
//
//        } catch (IOException e) {
//            String errorMessage = String.format("读取 INI 文件 '%s' 时发生 I/O 错误: %s", iniFilePath, e.getMessage());
//            logger.error(errorMessage, e);
//            throw new IOException(errorMessage, e);
//        } catch (java.util.NoSuchElementException e) {
//            String errorMessage = String.format("解析 INI 文件 '%s' '[Region]' 部分时缺少必要的键 (如 x, y, width, height): %s", iniFilePath, e.getMessage());
//            logger.error(errorMessage, e);
//            throw new IOException(errorMessage, e);
//        } catch (IllegalArgumentException e) {
//            String errorMessage = String.format("解析 INI 文件 '%s' '[Region]' 部分的键值时发生类型错误: %s", iniFilePath, e.getMessage());
//            logger.error(errorMessage, e);
//            throw new IOException(errorMessage, e);
//        } catch (ProcessException e) {
//            String errorMessage = String.format("加载或解析 INI 文件 '%s' 时发生未知错误: %s", iniFilePath, e.getMessage());
//            logger.error(errorMessage, e);
//            throw new IOException(errorMessage, e);
//        }
//    }

    /**
     * 处理目录中的多帧图像
     * @param inputDirPath 输入目录路径
     * @param algorithmName 算法名称
     * @return 处理结果响应对象
     * @throws IOException 当发生I/O错误时抛出
     */
    public MultiFrameResultResponse processDirectory(String inputDirPath, String algorithmName) throws IOException {
        logger.info("开始处理多帧图像: {}, 算法: {}", inputDirPath, algorithmName);

        List<String> filePathsList;
        try (Stream<Path> paths = Files.list(Paths.get(inputDirPath))) {
            filePathsList = paths
                    .filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("无法读取输入目录中的文件列表: {}", inputDirPath, e);
            throw new ProcessException("无法读取输入目录 '" + inputDirPath + "': " + e.getMessage(), e);
        }

        if (filePathsList.isEmpty()) {
            logger.warn("输入目录 {} 为空或不包含文件。", inputDirPath);
            throw new ProcessException("指定的输入目录不包含任何文件。");
        }

        String commaSeparatedFilePaths = String.join(",", filePathsList);
        int numFiles = filePathsList.size();
        logger.info("共有 {} 个图像文件。准备调用C++处理。", numFiles);
        logger.debug("文件列表: {}", commaSeparatedFilePaths.substring(0, Math.min(200, commaSeparatedFilePaths.length())));

        logger.info("正在从 ConfigService 加载配置...");
        ConfigDto config = configService.getConfig();
        ConfigDto.Region region = config.getRegion();
        CropBox.ByValue cropBoxConfig = new CropBox.ByValue(
                region.getX(),
                region.getY(),
                region.getWidth(),
                region.getHeight()
        );
        logger.info("从 ConfigService 成功加载裁剪框: x={}, y={}, width={}, height={}",
                region.getX(), region.getY(), region.getWidth(), region.getHeight());


        InputData.ByReference inputData = new InputData.ByReference();
        OutputData.ByReference outputData = new OutputData.ByReference();
        int processStatus = -1;
        String resultOutputDir = null;

        //CropBox.ByValue cropBoxConfig = loadCropBoxFromIni();

        try {
            inputData.mode = 1; // 多帧模式
            inputData.algorithmName = algorithmName;
            inputData.originalBase64 = commaSeparatedFilePaths;
            inputData.croppedBase64 = commaSeparatedFilePaths;
            inputData.fileNum = numFiles;
            inputData.id = 0;
            inputData.imgType = 0;
            inputData.crop = cropBoxConfig;

            logger.info("调用C++ processImageWrapper (多帧模式)...");
            processStatus = NativeMultiFrameLib.INSTANCE.processImageWrapper(inputData, outputData);
            logger.info("C++ processImageWrapper (多帧模式) 返回状态: {}", processStatus);

            if (processStatus == 0) {
                resultOutputDir = outputData.outputDir; // 从C++获取输出目录
                if (resultOutputDir == null || resultOutputDir.trim().isEmpty()) {
                    logger.error("C++ (多帧) 处理成功但未返回有效的输出目录路径。");
                    throw new ProcessException("核心算法处理成功但未指定输出目录。");
                }
                logger.info("C++ (多帧) 处理成功。消息: '{}', 输出目录: '{}'", outputData.message, resultOutputDir);

                List<String> originalFileNamesOnly = filePathsList.stream()
                        .map(fullPath -> new File(fullPath).getName())
                        .collect(Collectors.toList());
                List<String> interestImageNames = new ArrayList<>();
                List<String> outputImageNames = new ArrayList<>();

                for (String originalNameWithExt : originalFileNamesOnly) {
                    String baseName = originalNameWithExt.substring(0, originalNameWithExt.lastIndexOf('.'));
                    interestImageNames.add("roi_" + baseName + ".png");
                    outputImageNames.add(baseName + ".png");
                }

                MultiFrameResultResponse.ResultFiles resultFiles =
                        new MultiFrameResultResponse.ResultFiles(originalFileNamesOnly, interestImageNames, outputImageNames);

                return new MultiFrameResultResponse(
                        true,
                        resultOutputDir,
                        resultFiles,
                        outputData.message != null ? outputData.message : "处理成功",
                        outputData.fileNum // C++ 返回的实际处理/生成的文件数
                );

            } else {
                String errorMsg = "C++ (多帧) 处理失败。状态: " + processStatus;
                if (outputData.message != null && !outputData.message.isEmpty()) {
                    errorMsg += ", 消息: " + outputData.message;
                }
                logger.error(errorMsg);
                throw new ProcessException(errorMsg);
            }
        } catch (UnsatisfiedLinkError ule) {
            logger.error("JNA链接错误: {}", ule.getMessage(), ule);
            throw new ProcessException("无法链接到多帧核心处理库。确保 XJYTXFCV_multi 及其依赖项正确。", ule);
        }
        finally {
            if (outputData != null && outputData.getPointer() != null) {
                try {
                    NativeMultiFrameLib.INSTANCE.freeOutputData(outputData);
                    logger.info("已调用 freeOutputData (多帧) 清理 OutputData。");
                } catch (ProcessException e) {
                    logger.error("调用 freeOutputData (多帧) 时发生错误。", e);
                }
            }
        }
    }
}