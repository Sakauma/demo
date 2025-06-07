// src/main/java/com/demo/imgProcess/MultiFrameProcessorService.java
package com.demo.imgProcess;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.Pointer;

import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.demo.imgProcess.dto.MultiFrameResultResponse;

@Service
public class MultiFrameProcessorCpp {
    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            System.setProperty("jna.library.path", "./lib");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MultiFrameProcessorCpp.class);

    public static class CropBox extends Structure {
        public int x; public int y; public int width; public int height;
        public CropBox() { super(ALIGN_DEFAULT); }
        protected List<String> getFieldOrder() { return Arrays.asList("x", "y", "width", "height"); }
        public static class ByValue extends CropBox implements Structure.ByValue {
            public ByValue(int x, int y, int width, int height){ this.x=x; this.y=y; this.width=width; this.height=height; }
            public ByValue(){}
        }
    }

    public static class InputData extends Structure {
        public String originalBase64;
        public String croppedBase64;
        public String algorithmName;
        public int fileNum;
        public int mode;
        public int imgType;
        public int id;
        public CropBox.ByValue crop;
        public InputData() { super(ALIGN_DEFAULT); }
        protected List<String> getFieldOrder() { return Arrays.asList("originalBase64", "croppedBase64", "algorithmName", "fileNum", "mode", "imgType", "id", "crop"); }
        public static class ByReference extends InputData implements Structure.ByReference {}
    }

    public static class OutputData extends Structure {
        public String processedBase64;
        public Pointer result;
        public int result_length;
        public String message;
        public String outputDir;
        public int fileNum;
        public OutputData() { super(ALIGN_DEFAULT); }
        protected List<String> getFieldOrder() { return Arrays.asList("processedBase64", "result", "result_length", "message", "outputDir", "fileNum"); }
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
        public static class ByReference extends OutputData implements Structure.ByReference {}
    }

    public interface NativeMultiFrameLib extends Library {
        NativeMultiFrameLib INSTANCE = Native.load("XJYTXFCV_multi", NativeMultiFrameLib.class);
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);
        void freeOutputData(OutputData.ByReference output);
    }

    private CropBox.ByValue loadCropBoxFromIni(String iniFilePath) {
        File iniFile = new File(iniFilePath);

        if (!iniFile.exists() || !iniFile.isFile()) {
            String errorMessage = String.format("必需的 INI 配置文件 '%s' 未找到或不是一个有效文件。", iniFilePath);
            logger.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }

        try (FileReader reader = new FileReader(iniFile)) {
            Ini ini = new Ini();
            ini.load(reader);
            Ini.Section regionSection = ini.get("Region");

            if (regionSection == null) {
                String errorMessage = String.format("INI 文件 '%s' 中必需的 '[Region]' 部分未找到。", iniFilePath);
                logger.error(errorMessage);
                throw new RuntimeException(errorMessage);
            }

            int x = regionSection.get("x", int.class);
            int y = regionSection.get("y", int.class);
            int width = regionSection.get("width", int.class);
            int height = regionSection.get("height", int.class);

            logger.info("从 INI 文件 '{}' 的 '[Region]' 部分成功加载裁剪框: x={}, y={}, width={}, height={}",
                    iniFilePath, x, y, width, height);
            return new CropBox.ByValue(x, y, width, height);

        } catch (IOException e) {
            String errorMessage = String.format("读取 INI 文件 '%s' 时发生 I/O 错误: %s", iniFilePath, e.getMessage());
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        } catch (java.util.NoSuchElementException e) {
            String errorMessage = String.format("解析 INI 文件 '%s' '[Region]' 部分时缺少必要的键 (如 x, y, width, height): %s", iniFilePath, e.getMessage());
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        } catch (IllegalArgumentException e) {
            String errorMessage = String.format("解析 INI 文件 '%s' '[Region]' 部分的键值时发生类型错误 (例如值不是有效整数): %s", iniFilePath, e.getMessage());
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = String.format("加载或解析 INI 文件 '%s' 时发生未知错误: %s", iniFilePath, e.getMessage());
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }


    public MultiFrameResultResponse processDirectory(String inputDirPath, String algorithmName) {
        logger.info("开始处理多帧目录: {}, 算法: {}", inputDirPath, algorithmName);

        List<String> filePathsList;
        try (Stream<Path> paths = Files.list(Paths.get(inputDirPath))) {
            filePathsList = paths
                    .filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("无法读取输入目录中的文件列表: {}", inputDirPath, e);
            throw new RuntimeException("无法读取输入目录 '" + inputDirPath + "': " + e.getMessage(), e);
        }

        if (filePathsList.isEmpty()) {
            logger.warn("输入目录 {} 为空或不包含文件。", inputDirPath);
            throw new RuntimeException("指定的输入目录不包含任何文件。");
        }

        String commaSeparatedFilePaths = String.join(",", filePathsList);
        int numFiles = filePathsList.size();
        logger.info("Java层列举到 {} 个文件。准备调用C++处理。", numFiles);
        logger.debug("文件列表: {}", commaSeparatedFilePaths.substring(0, Math.min(200, commaSeparatedFilePaths.length())));


        InputData.ByReference inputData = new InputData.ByReference();
        OutputData.ByReference outputData = new OutputData.ByReference();
        int processStatus = -1;
        String resultOutputDir = null;

        String iniFilePath = Paths.get("lib", "data.ini").toString();
        CropBox.ByValue cropBoxConfig = loadCropBoxFromIni(iniFilePath);

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
                    throw new RuntimeException("核心算法处理成功但未指定输出目录。");
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
                throw new RuntimeException(errorMsg);
            }
        } catch (UnsatisfiedLinkError ule) {
            logger.error("JNA链接错误 (processImageWrapper/freeOutputData): {}", ule.getMessage(), ule);
            throw new RuntimeException("无法链接到多帧核心处理库(process/free)。确保 XJYTXFCV_multi.dll 在路径中。", ule);
        }
        finally {
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