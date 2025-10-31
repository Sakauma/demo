package com.demo.service.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;

import com.demo.dto.MultiFrameResultResponse;
import com.demo.exception.ProcessException;
import com.demo.service.ConfigService;
import com.demo.dto.ConfigDto;
import com.demo.service.FeatureParserService;
import com.demo.service.FeaturePersistenceService;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多帧图像处理服务，通过 JNA (Java Native Access) 与 C++ 核心库交互。
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
        if (osName.contains("windows") || osName.contains("linux") ) {
            System.setProperty("jna.library.path", "./lib");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MultiFrameProcessorCpp.class);
    private final ConfigService configService;
    private final FeatureParserService featureParserService;
    private final FeaturePersistenceService featurePersistenceService;

    /**
     * 构造函数，通过 Spring 的依赖注入初始化 ConfigService。
     * @param configService 配置服务，用于获取应用配置，如裁剪参数。
     */
    @Autowired
    public MultiFrameProcessorCpp(ConfigService configService,
                                  FeatureParserService featureParserService,
                                  FeaturePersistenceService featurePersistenceService) {
        this.configService = configService;
        this.featureParserService = featureParserService;
        this.featurePersistenceService = featurePersistenceService;
        logger.info("ConfigService 已注入到 MultiFrameProcessorCpp。");
    }

    /**
     * JNA 结构体，映射 C++ 中的 `CropBox` 结构体。
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
         * CropBox 的子类，实现了 Structure.ByValue 标记接口。
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
     * JNA 结构体，映射 C++ 中的 `InputPathSet` 结构体。
     */
    public static class InputPathSet extends Structure {
        public String outputDir; // 对应 C++: char * outputDir
        public String par_path;  // 对应 C++: char * par_path
        public String trackPath; // 对应 C++: char * trackPath
        public String inImgDir;  // 对应 C++: char * inImgDir

        public InputPathSet() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            // 顺序必须与 C++ XJYTXFXCV.h 中的 InputPathSet 严格一致
            return Arrays.asList("outputDir", "par_path", "trackPath", "inImgDir");
        }

        /**
         * ByValue 内部类，用于在 InputData 中进行值传递（嵌套）。
         */
        public static class ByValue extends InputPathSet implements Structure.ByValue {
            public ByValue() {}
        }
    }

//    /**
//     * JNA 结构体，映射 C++ 中用于接收输入数据的 `InputData` 结构体。
//     */
//    public static class InputData extends Structure {
//        public String originalBase64; // 在多帧模式下，这是一个逗号分隔的文件绝对路径列表。
//        public String croppedBase64;  // 在多帧模式下，通常与 originalBase64 相同。
//        public String resultDir;      // 保存结果路径
//        public String algorithmName;  // 使用的算法名称。
//        public int fileNum;           // 要处理的文件总数。
//        public int mode;              // 处理模式（例如，1 代表多帧）。
//        public int imgType;           // 图像类型（未使用）。
//        public int id;                // 请求ID（未使用）。
//        public CropBox.ByValue crop;  // 裁剪框参数（按值传递）。
//
//        public InputData() {
//            super(ALIGN_DEFAULT);
//        }
//
//        // 定义字段顺序，必须与 C++ 侧一致
//        protected List<String> getFieldOrder() {
//            return Arrays.asList("originalBase64", "croppedBase64", "resultDir", "algorithmName", "fileNum", "mode", "imgType", "id", "crop");
//        }
//
//        /**
//         * 实现了 Structure.ByReference 的内部类。
//         * JNA 会传递指向该结构体实例的指针给 C++ 函数。
//         */
//        public static class ByReference extends InputData implements Structure.ByReference {
//        }
//    }

    /**
     * JNA 结构体，映射 C++ 中用于接收输入数据的 `InputData` 结构体。
     */
    public static class InputData extends Structure {
        public InputPathSet.ByValue inputPathSet; // [新增] 对应 C++: InputPathSet inputPathSet;

        public String algorithmName;  // 对应 C++: const char *algorithmName
        public int fileNum;           // 对应 C++: int fileNum
        public int mode;              // 对应 C++: int mode
        public int imgType;           // 对应 C++: int imgType
        public int id;                // 对应 C++: int id
        public CropBox.ByValue crop;  // 对应 C++: CropBox crop

        public InputData() {
            super(ALIGN_DEFAULT);
        }

        // 定义字段顺序，必须与 C++ 侧 XJYTXFXCV.h 一致
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("inputPathSet", "algorithmName", "fileNum", "mode", "imgType", "id", "crop");
        }

        public static class ByReference extends InputData implements Structure.ByReference {}
    }

    /**
     * JNA 结构体，映射 C++ 中的 `OutputPathSet` 结构体。
     */
    public static class OutputPathSet extends Structure {
        public String feature_path; // 对应 C++: char * feature_path
        public String outImgDir;    // 对应 C++: char * outImgDir

        public OutputPathSet() {
            super(ALIGN_DEFAULT);
        }

        @Override
        protected List<String> getFieldOrder() {
            // 顺序必须与 C++ XJYTXFXCV.h 中的 OutputPathSet 严格一致
            return Arrays.asList("feature_path", "outImgDir");
        }
    }

//    /**
//     * JNA 结构体，映射 C++ 中用于返回处理结果的 `OutputData` 结构体。
//     */
//    public static class OutputData extends Structure {
//        public String processedBase64; // 在多帧模式下，此字段通常不被使用。
//        public Pointer result;         // 指向由 C++ 分配的结果数据（可能是特征数组）的指针。
//        public int result_length;      // 结果数据的长度。
//        public String message;         // C++ 返回的状态或调试消息。
//        public String outputDir;       // C++ 处理后生成所有结果文件的输出目录路径。
//        public int fileNum;            // C++ 实际成功处理的文件数量。
//
//        public OutputData() {
//            super(ALIGN_DEFAULT);
//        }
//
//        // 定义字段顺序
//        protected List<String> getFieldOrder() {
//            return Arrays.asList("processedBase64", "result", "result_length", "message", "outputDir", "fileNum");
//        }
//
//        /**
//         * 从 JNA 指针中安全地提取 float 数组。
//         * @return 一个包含结果数据的 float 数组，如果指针为空或长度为0，则返回空数组。
//         */
//        public float[] getResultArray() {
//            if (this.result_length <= 0 || this.result == null) {
//                return new float[0];
//            }
//            try {
//                return this.result.getFloatArray(0, this.result_length);
//            } catch (Exception e) {
//                logger.error("尝试从 OutputData.result 读取浮点数组时出错 (长度: {}): {}", this.result_length, e.getMessage());
//                return new float[0];
//            }
//        }
//
//        /**
//         * 按引用传递的标记子类。
//         */
//        public static class ByReference extends OutputData implements Structure.ByReference {
//        }
//    }

    /**
     * JNA 结构体，映射 C++ 中用于返回处理结果的 `OutputData` 结构体。
     */
    public static class OutputData extends Structure {
        public OutputPathSet outputPathSet; // 对应 C++: OutputPathSet outputPathSet;
        public String message;          // 对应 C++: char *message
        public int fileNum;             // 对应 C++: int fileNum

        public OutputData() {
            super(ALIGN_DEFAULT);
        }

        // 定义字段顺序
        @Override
        protected List<String> getFieldOrder() {
            return Arrays.asList("outputPathSet", "message", "fileNum");
        }

        public static class ByReference extends OutputData implements Structure.ByReference {}
    }

    /**
     * JNA 接口，定义了要从多帧处理原生库中调用的函数。
     */
    public interface NativeMultiFrameLib extends Library {
        /**
         * 加载名为 "XJYTXFXCV_multi" 的 C++ 动态链接库。
         */
        NativeMultiFrameLib INSTANCE = Native.load("XJYTXFXCV_multi", NativeMultiFrameLib.class);

        /**
         * 映射 C++ 库中的 `processImageWrapper` 函数。
         * @param input C++函数期望接收的输入数据结构体（通过指针传递）。
         * @param output C++函数将填充的结果数据结构体（通过指针传递）。
         * @return 返回一个整型状态码，0 表示成功。
         */
        int processImageWrapper(InputData.ByReference input, OutputData.ByReference output);

        /**
         * 映射 C++ 库中的 `freeOutputData` 函数，用于释放 C++ 分配的内存。
         * @param output 需要被释放内存的结构体。
         */
        void freeOutputData(OutputData.ByReference output);
    }

    // 被注释掉的代码块，原意可能是从.ini文件加载配置，现在已被ConfigService替代。
    // 保留注释可以帮助理解代码的演进历史。
    /*
    private CropBox.ByValue loadCropBoxFromIni() throws IOException { ... }
    */

//    /**
//     * 处理通过API上传的多个文件，执行多帧图像识别。
//     * @param imageFiles    从Controller接收到的图像MultipartFile列表。
//     * @param trackFile     GJ 模式 (mode=2) 所需的轨迹文件。
//     * @param algorithmName 要使用的算法名称。
//     * @param mode          处理模式 (1=多帧, 2=GJ)。
//     * @return 包含处理结果的详细信息。
//     * @throws IOException 如果在创建临时文件或目录时发生 I/O 错误。
//     */
//    public MultiFrameResultResponse processUploadedFiles(List<MultipartFile> imageFiles,
//                                                         MultipartFile trackFile,
//                                                         String algorithmName,
//                                                         int mode) throws IOException {
//        // 1. 创建一个唯一的临时目录来安全地存放上传的文件
//        Path tempDir = Files.createTempDirectory("multi-frame-upload-" + UUID.randomUUID().toString());
//        logger.info("为本次请求创建了临时目录: {}", tempDir.toAbsolutePath());
//
//        // 我们需要一个有序的 Path 列表，以便传递给持久化服务
//        List<Path> orderedRawFilePaths = new ArrayList<>();
//        List<String> originalImageFileNames = new ArrayList<>();
//
//        try {
//            //List<String> tempImageFilePaths = new ArrayList<>();
//            //List<String> originalImageFileNames = new ArrayList<>();
//
//            for (MultipartFile file : imageFiles) {
//                if (file.isEmpty()) {
//                    continue;
//                }
//                // 保证文件名安全，防止路径遍历攻击
//                String originalFileName = file.getOriginalFilename();
//                Path tempFile = tempDir.resolve(originalFileName).normalize();
//                if (originalFileName == null || originalFileName.contains("..")) {
//                    throw new ProcessException("包含无效字符的非法文件名: " + originalFileName);
//                }
//
//                // 在临时目录下创建文件并从上传流中复制内容
//                Path tempFile = tempDir.resolve(originalFileName).normalize();
//                Path parentDir = tempFile.getParent();
//                if (parentDir != null && !Files.exists(parentDir)) {
//                    Files.createDirectories(parentDir);
//                }
//                try (InputStream inputStream = file.getInputStream()) {
//                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
//                }
//
//                tempImageFilePaths.add(tempFile.toAbsolutePath().toString());
//                originalImageFileNames.add(originalFileName);
//            }
//
//            // 保存轨迹文件 (如果 mode == 2)
//            if (tempImageFilePaths.isEmpty()) {
//                throw new ProcessException("上传的文件均为空或无效，无法处理。");
//            }
//
//            String tempTrackFilePath = null;
//            if (mode == 2) {
//                if (trackFile == null || trackFile.isEmpty()) {
//                    throw new ProcessException("模式 2 (GJDeal) 必须提供一个轨迹 (track) 文件。");
//                }
//                String originalTrackFileName = trackFile.getOriginalFilename();
//                if (originalTrackFileName == null || originalTrackFileName.contains("..")) {
//                    throw new ProcessException("包含无效字符的非法轨迹文件名: " + originalTrackFileName);
//                }
//
//                Path tempTrackFile = tempDir.resolve(originalTrackFileName).normalize();
//                try (InputStream inputStream = trackFile.getInputStream()) {
//                    Files.copy(inputStream, tempTrackFile, StandardCopyOption.REPLACE_EXISTING);
//                }
//                tempTrackFilePath = tempTrackFile.toAbsolutePath().toString();
//                logger.info("轨迹文件已保存到: {}", tempTrackFilePath);
//            }
//
//            String imageDirectoryPath = tempDir.toAbsolutePath().toString();
//            int numImageFiles = tempImageFilePaths.size();
//
//            // 2. 调用重构后的核心处理逻辑
//            return processFiles(
//                    imageDirectoryPath,   // C++ 的 inImgDir
//                    tempTrackFilePath,    // C++ 的 trackPath
//                    originalImageFileNames,
//                    algorithmName,
//                    mode,
//                    numImageFiles
//            );
//
//        } finally {
//            // 5. 【关键】无论成功与否，都必须清理临时文件和目录
//            logger.info("处理完成，开始清理临时目录: {}", tempDir.toAbsolutePath());
//            try (Stream<Path> walk = Files.walk(tempDir)) {
//                walk.sorted(Comparator.reverseOrder())
//                        .map(Path::toFile)
//                        .forEach(File::delete);
//                logger.info("临时目录已成功清理。");
//            } catch (IOException e) {
//                // 记录清理错误，但不向上抛出，以免覆盖原始的业务异常
//                logger.error("清理临时目录 {} 时发生严重错误。", tempDir.toAbsolutePath(), e);
//            }
//        }
//    }

    /**
     * 处理通过API上传的多个文件，执行多帧图像识别。
     * [!! 已重构 !!]
     * 此方法现在还负责在C++调用成功后、临时文件删除前，
     * 立即调用持久化服务，以便传递原始DAT文件数据。
     *
     * @param imageFiles    从Controller接收到的图像MultipartFile列表。
     * @param trackFile     GJ 模式 (mode=2) 所需的轨迹文件。
     * @param algorithmName 要使用的算法名称。
     * @param mode          处理模式 (1=多帧, 2=GJ)。
     * @return 包含处理结果的详细信息。
     * @throws IOException 如果在创建临时文件或目录时发生 I/O 错误。
     */
    public MultiFrameResultResponse processUploadedFiles(List<MultipartFile> imageFiles,
                                                         MultipartFile trackFile,
                                                         String algorithmName,
                                                         int mode) throws IOException {

        // 1. 创建一个唯一的临时目录来安全地存放上传的文件
        // [!! 已更正 !!] 我们只创建根临时目录，不创建 'IMG0'
        Path tempDir = Files.createTempDirectory("multi-frame-upload-" + UUID.randomUUID().toString());
        logger.info("为本次请求创建了临时目录: {}", tempDir.toAbsolutePath());

        // [!! 新增 !!] 我们需要一个有序的 Path 列表，以便传递给持久化服务
        List<Path> orderedRawFilePaths = new ArrayList<>();
        List<String> originalImageFileNames = new ArrayList<>();

        try {
            // 2. 将所有上传的图像文件保存到临时目录
            for (MultipartFile file : imageFiles) {
                if (file.isEmpty()) {
                    continue;
                }
                // 保证文件名安全，防止路径遍历攻击
                String originalFileName = file.getOriginalFilename();
                if (originalFileName == null || originalFileName.contains("..")) {
                    throw new ProcessException("包含无效字符的非法文件名: " + originalFileName);
                }

                // [!! 已更正 !!] 将文件保存在 根临时目录 (tempDir) 中
                Path tempFile = tempDir.resolve(originalFileName).normalize();
                Path parentDir = tempFile.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                orderedRawFilePaths.add(tempFile); // [!! 新增 !!] 保存 Path 对象
                originalImageFileNames.add(originalFileName);
            }

            // 3. 保存轨迹文件 (如果 mode == 2)
            if (orderedRawFilePaths.isEmpty()) {
                throw new ProcessException("上传的文件均为空或无效，无法处理。");
            }

            String tempTrackFilePath = null;
            if (mode == 2) {
                if (trackFile == null || trackFile.isEmpty()) {
                    throw new ProcessException("模式 2 (GJDeal) 必须提供一个轨迹 (track) 文件。");
                }
                String originalTrackFileName = trackFile.getOriginalFilename();
                if (originalTrackFileName == null || originalTrackFileName.contains("..")) {
                    throw new ProcessException("包含无效字符的非法轨迹文件名: " + originalTrackFileName);
                }

                // [!! 已更正 !!] 轨迹文件也保存在 根临时目录 (tempDir)
                Path tempTrackFile = tempDir.resolve(originalTrackFileName).normalize();
                try (InputStream inputStream = trackFile.getInputStream()) {
                    Files.copy(inputStream, tempTrackFile, StandardCopyOption.REPLACE_EXISTING);
                }
                tempTrackFilePath = tempTrackFile.toAbsolutePath().toString();
                logger.info("轨迹文件已保存到: {}", tempTrackFilePath);
            }

            // [!! 已更正 !!] 我们将 *根临时目录* 的路径传递给 processFiles
            String imageDirectoryPathForCpp = tempDir.toAbsolutePath().toString();
            int numImageFiles = orderedRawFilePaths.size();

            // 4. 调用核心处理逻辑 (该方法已被重构为返回 OutputData)
            // (processFiles 方法内部会负责附加 "/IMG0" 后缀)
            OutputData.ByReference outputData = processFiles(
                    imageDirectoryPathForCpp,   // 这是 .../temp-dir 路径
                    tempTrackFilePath,
                    algorithmName,
                    mode,
                    numImageFiles
            );

            // 5. [!! 核心重构 !!] 立即执行持久化
            // (此时，C++ 已执行完毕，但 'tempDir' 中的原始 .dat 文件尚未被删除)
            String featureDatPath = outputData.outputPathSet.feature_path;
            String resultImgDir = outputData.outputPathSet.outImgDir;

            if (featureDatPath == null || featureDatPath.trim().isEmpty()) {
                logger.warn("C++ 未返回 feature_path，跳过持久化。");
            } else {
                try {
                    // 5A. 解析 C++ 生成的 Feature.dat
                    Map<String, List<? extends Number>> features = this.featureParserService.parseFeatureFile(featureDatPath);

                    // 5B. 推断 AnalysisID
                    String analysisId = Paths.get(featureDatPath).getParent().getFileName().toString();

                    // 5C. [!! 关键 !!] 调用持久化，并传入原始文件路径列表
                    logger.info("开始持久化 (在多帧处理流程中)... AnalysisID: {}", analysisId);
                    this.featurePersistenceService.persistFeatures(features, analysisId, orderedRawFilePaths);
                    logger.info("持久化完成 (在多帧处理流程中)。");

                } catch (Exception e) {
                    // 记录错误，但不要让它中断对前端的响应
                    logger.error("在 infer_multi_frame 流程中持久化失败: {}", e.getMessage(), e);
                }
            }

            // 6. 构建返回给 Controller 的 Response
            MultiFrameResultResponse response = buildResponse(
                    resultImgDir,
                    outputData.message,
                    outputData.fileNum,
                    originalImageFileNames // 使用我们之前保存的原始文件名
            );

            // 7. 释放 C++ 内存
            if (outputData != null && outputData.getPointer() != null) {
                try {
                    NativeMultiFrameLib.INSTANCE.freeOutputData(outputData);
                    logger.info("已调用 freeOutputData (多帧) 清理 OutputData。");
                } catch (Exception e) {
                    logger.error("调用 freeOutputData (多帧) 时发生错误。", e);
                }
            }

            return response;

        } finally {
            // 8. 【关键】无论成功与否，都必须清理临时文件和目录
            logger.info("处理完成，开始清理临时目录: {}", tempDir.toAbsolutePath());
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                logger.info("临时目录已成功清理。");
            } catch (IOException e) {
                // 记录清理错误，但不向上抛出
                logger.error("清理临时目录 {} 时发生严重错误。", tempDir.toAbsolutePath(), e);
            }
        }
    }

    /**
     * 核心处理逻辑，被 processDirectory 和 processUploadedFiles 共用。
     *
     * @param imageDirectoryPath 待处理图像文件所在的目录 (对应 C++ inImgDir)。
     * @param trackFilePath      轨迹文件的绝对路径 (对应 C++ trackPath)。
     * @param algorithmName      算法名称。
     * @param mode               处理模式 (1 或 2)。
     * @param numFiles           图像文件数量。
     * @return 处理结果。
     */
    private OutputData.ByReference processFiles(
            String imageDirectoryPath,
            String trackFilePath,
            String algorithmName,
            int mode,
            int numFiles) throws IOException {

        ConfigDto config = configService.getConfig();
        ConfigDto.Region region = config.getRegion();
        CropBox.ByValue cropBoxConfig = new CropBox.ByValue(
                region.getX(), region.getY(), region.getWidth(), region.getHeight()
        );

        Path projectRoot = getProjectRootPath();

        // 1. C++ DLL 的输出根目录 (例如: ".../result/")
        // C++ DLL 将在此目录下创建带时间戳的 'img' 和 'feature' 文件夹
        Path resultBasePath = projectRoot.resolve("result");
        if (!Files.exists(resultBasePath)) {
            Files.createDirectories(resultBasePath);
        }
        String resultBaseDirString = resultBasePath.toAbsolutePath().toString();

        // 2. 神经网络参数路径
        // C++ 使用 "../Parameter/tfImg.pb"
        Path parPath = projectRoot.resolve("Parameter").resolve("tfImg.pb");
        String parPathString = parPath.toAbsolutePath().toString();
        if (!Files.exists(parPath)) {
            logger.warn("神经网络参数文件未找到: {}。C++ 调用可能因此失败。", parPathString);
        } else {
            logger.info("找到神经网络参数文件: {}", parPathString);
        }

        InputData.ByReference inputData = new InputData.ByReference();
        OutputData.ByReference outputData = new OutputData.ByReference();
        int processStatus = -1;

        //try {
            // 1. 填充 InputPathSet
            inputData.inputPathSet = new InputPathSet.ByValue();
            Path basePath = Paths.get(imageDirectoryPath);
            Path finalImg0Path = basePath.resolve("IMG0");
            inputData.inputPathSet.inImgDir = finalImg0Path.toAbsolutePath().toString();
            inputData.inputPathSet.outputDir = resultBaseDirString; // C++ 需要这个根目录
            inputData.inputPathSet.par_path = parPathString;
            inputData.inputPathSet.trackPath = trackFilePath; // 如果 mode=1，可以为 null

            logger.info("C++ (多帧) 接收的最终 'inImgDir' 路径: {}", inputData.inputPathSet.inImgDir);

            // 2. 填充 InputData 的其余字段
            inputData.mode = mode;
            inputData.algorithmName = algorithmName;
            inputData.fileNum = numFiles;
            inputData.crop = cropBoxConfig;
            inputData.imgType = 1; // 默认值，与 C++ demo 一致
            inputData.id = 0; // 默认值

            logger.info("调用C++ processImageWrapper (多帧模式)...");
            processStatus = NativeMultiFrameLib.INSTANCE.processImageWrapper(inputData, outputData);
            logger.info("C++ processImageWrapper (多帧模式) 返回状态: {}", processStatus);

            if (processStatus == 0) {
                if (outputData.outputPathSet == null) {
                    throw new ProcessException("核心算法处理成功 (status=0) 但未返回 outputPathSet。");
                }
                String resultOutputDir = outputData.outputPathSet.outImgDir;
                String featureFilePath = outputData.outputPathSet.feature_path;

                if (resultOutputDir == null || resultOutputDir.trim().isEmpty() ||
                        featureFilePath == null || featureFilePath.trim().isEmpty()) {
                    throw new ProcessException("核心算法返回的路径无效。");
                }
                logger.info("C++ (多帧) 处理成功。消息: '{}', 图像输出目录: '{}', 特征文件: '{}'",
                        outputData.message, resultOutputDir, featureFilePath);

                return outputData;

//            if (processStatus == 0) {
//                if (outputData.outputPathSet == null) {
//                    throw new ProcessException("核心算法处理成功 (status=0) 但未返回 outputPathSet。");
//                }
//
//                String resultOutputDir = outputData.outputPathSet.outImgDir;
//                String featureFilePath = outputData.outputPathSet.feature_path;
//
//                if (resultOutputDir == null || resultOutputDir.trim().isEmpty() ||
//                        featureFilePath == null || featureFilePath.trim().isEmpty()) {
//                    throw new ProcessException("核心算法返回的路径无效。");
//                }
//                logger.info("C++ (多帧) 处理成功。消息: '{}', 图像输出目录: '{}', 特征文件: '{}'", outputData.message, resultOutputDir, featureFilePath);
//
//                // C++成功执行后，直接从结果目录读取并排序文件名
//                List<String> generatedPngFiles;
//                try (Stream<Path> paths = Files.list(Paths.get(resultOutputDir))) {
//                    generatedPngFiles = paths
//                            .map(Path::getFileName)
//                            .map(Path::toString)
//                            .filter(name -> name.toLowerCase().endsWith(".png"))
//                            .collect(Collectors.toList());
//                }
//                // 使用自然排序对文件名进行排序
//                generatedPngFiles.sort(new NaturalOrderComparator());
//                logger.info("从结果目录 '{}' 读取并排序了 {} 个.png文件。", resultOutputDir, generatedPngFiles.size());
//
//                List<String> interestImageNames = new ArrayList<>();
//                // 假设 "roi_" 图像与结果图像一一对应
//                for (String pngFile : generatedPngFiles) {
//                    if (pngFile.toLowerCase().startsWith("roi_")) {
//                        // 如果已经是roi文件，则跳过，因为我们只关心主输出文件
//                        continue;
//                    }
//                    // 检查是否存在对应的 roi 文件
//                    Path roiPath = Paths.get(resultOutputDir, "roi_" + pngFile);
//                    if (Files.exists(roiPath)) {
//                        interestImageNames.add("roi_" + pngFile);
//                    }
//                }
//                // 过滤掉 roi_ 开头的文件，只保留主结果文件
//                List<String> outputImageNames = generatedPngFiles.stream()
//                        .filter(name -> !name.toLowerCase().startsWith("roi_"))
//                        .collect(Collectors.toList());
//
//                // 为每个结果帧找到它对应的原始 .dat 文件名
//                List<String> expandedOriginalNames = new ArrayList<>();
//                for (String pngName : outputImageNames) {
//                    // 移除帧号和扩展名来匹配原始文件名
//                    String pngBaseName = pngName.substring(0, pngName.lastIndexOf('_'));
//                    String originalFound = originalFileNamesOnly.stream()
//                            .map(ofn -> ofn.substring(0, ofn.lastIndexOf('.'))) // 获取原始文件的基础名
//                            .filter(ofnBase -> ofnBase.equals(pngBaseName))
//                            .findFirst()
//                            .orElse(originalFileNamesOnly.get(0)); // 如果找不到，默认用第一个
//                    expandedOriginalNames.add(originalFound + ".dat"); // 假设原始文件总是 .dat
//                }
//
//
//                MultiFrameResultResponse.ResultFiles resultFiles =
//                        new MultiFrameResultResponse.ResultFiles(expandedOriginalNames, interestImageNames, outputImageNames);
//
//                return new MultiFrameResultResponse(
//                        true,
//                        resultOutputDir, // [重要] 使用 C++ 返回的实际图像目录
//                        resultFiles,
//                        outputData.message != null ? outputData.message : "处理成功",
//                        outputData.fileNum
//                );
            } else {
                // [!! 修改 !!] 释放内存并抛出异常
                String errorMsg = "C++ (多帧) 处理失败。状态: " + processStatus + ", 消息: " + outputData.message;
                logger.error(errorMsg);
                if (outputData != null && outputData.getPointer() != null) {
                    NativeMultiFrameLib.INSTANCE.freeOutputData(outputData);
                }
                throw new ProcessException(errorMsg);
            }
//        } finally {
//            if (outputData != null && outputData.getPointer() != null) {
//                try {
//                    NativeMultiFrameLib.INSTANCE.freeOutputData(outputData);
//                    logger.info("已调用 freeOutputData (多帧) 清理 OutputData。");
//                } catch (Exception e) {
//                    logger.error("调用 freeOutputData (多帧) 时发生错误。", e);
//                }
//            }
//        }
    }

    // [!! 新增 !!] (从旧的 processFiles 复制过来)
    // 辅助方法，用于构建返回给前端的 Response
    private MultiFrameResultResponse buildResponse(String resultOutputDir, String message, int fileNumProcessed, List<String> originalFileNamesOnly) throws IOException {
        List<String> generatedPngFiles;
        try (Stream<Path> paths = Files.list(Paths.get(resultOutputDir))) {
            generatedPngFiles = paths
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.toLowerCase().endsWith(".png"))
                    .collect(Collectors.toList());
        }
        generatedPngFiles.sort(new NaturalOrderComparator());
        logger.info("从结果目录 '{}' 读取并排序了 {} 个.png文件。", resultOutputDir, generatedPngFiles.size());

        List<String> interestImageNames = new ArrayList<>();
        for (String pngFile : generatedPngFiles) {
            if (pngFile.toLowerCase().startsWith("roi_")) continue;
            Path roiPath = Paths.get(resultOutputDir, "roi_" + pngFile);
            if (Files.exists(roiPath)) {
                interestImageNames.add("roi_" + pngFile);
            }
        }
        List<String> outputImageNames = generatedPngFiles.stream()
                .filter(name -> !name.toLowerCase().startsWith("roi_"))
                .collect(Collectors.toList());

        List<String> expandedOriginalNames = new ArrayList<>();
        for (String pngName : outputImageNames) {
            String pngBaseName = pngName.substring(0, pngName.lastIndexOf('_'));
            String originalFound = originalFileNamesOnly.stream()
                    .map(ofn -> ofn.substring(0, ofn.lastIndexOf('.')))
                    .filter(ofnBase -> ofnBase.equals(pngBaseName))
                    .findFirst()
                    .orElse(originalFileNamesOnly.get(0));
            expandedOriginalNames.add(originalFound + ".dat");
        }

        MultiFrameResultResponse.ResultFiles resultFiles =
                new MultiFrameResultResponse.ResultFiles(expandedOriginalNames, interestImageNames, outputImageNames);

        return new MultiFrameResultResponse(
                true,
                resultOutputDir,
                resultFiles,
                message != null ? message : "处理成功",
                fileNumProcessed
        );
    }

    /**
     * 获取项目根目录，用于确定统一的输出路径。
     * @return 项目根目录的Path对象
     */
    private Path getProjectRootPath() {
        try {
            URL location = MultiFrameProcessorCpp.class.getProtectionDomain().getCodeSource().getLocation();
            Path basePath;
            if ("jar".equals(location.getProtocol())) {
                String jarPathString = location.toURI().getSchemeSpecificPart();
                int bangIndex = jarPathString.indexOf('!');
                if (bangIndex != -1) {
                    jarPathString = jarPathString.substring(0, bangIndex);
                }
                Path jarFile = Paths.get(new URI(jarPathString));
                basePath = jarFile.getParent().getParent();
            } else {
                Path classesPath = Paths.get(location.toURI());
                basePath = classesPath.getParent().getParent();
            }
            return basePath;
        } catch (Exception e) {
            logger.error("无法动态确定项目根目录，将回退到使用当前工作目录。", e);
            return Paths.get(".");
        }
    }

    /**
     * 【新】自定义比较器，用于实现文件名的自然排序。
     * 例如: "file_2.png" 会排在 "file_10.png" 之前。
     */
    class NaturalOrderComparator implements Comparator<String> {
        private final Pattern NUMERICAL_PATTERN = Pattern.compile("(\\D*)(\\d+)(.*)");

        @Override
        public int compare(String s1, String s2) {
            Matcher m1 = NUMERICAL_PATTERN.matcher(s1);
            Matcher m2 = NUMERICAL_PATTERN.matcher(s2);

            while (m1.find() && m2.find()) {
                String prefix1 = m1.group(1);
                String prefix2 = m2.group(1);
                if (!prefix1.equals(prefix2)) {
                    return prefix1.compareTo(prefix2);
                }

                String numStr1 = m1.group(2);
                String numStr2 = m2.group(2);
                if (!numStr1.equals(numStr2)) {
                    try {
                        long num1 = Long.parseLong(numStr1);
                        long num2 = Long.parseLong(numStr2);
                        if (num1 != num2) {
                            return Long.compare(num1, num2);
                        }
                    } catch (NumberFormatException e) {
                        // Fallback to string comparison if not a valid long
                        return numStr1.compareTo(numStr2);
                    }
                }

                s1 = m1.group(3);
                s2 = m2.group(3);
                m1 = NUMERICAL_PATTERN.matcher(s1);
                m2 = NUMERICAL_PATTERN.matcher(s2);
            }

            return s1.compareTo(s2);
        }
    }
}