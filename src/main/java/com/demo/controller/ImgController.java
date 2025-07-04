package com.demo.controller;
import com.demo.service.jna.ImgProcessorCpp;
import com.demo.service.jna.MultiFrameProcessorCpp;
import com.demo.dto.ConfigDto;

import com.demo.util.ConvertDatToImg;
import com.demo.util.CropImg;
import com.demo.util.ParseCoord;
import com.demo.dto.FolderPathRequest;
import com.demo.dto.MultiFrameResultResponse;
import com.demo.dto.FeatureDataResponse;
import com.demo.service.FeatureParserService;
import com.demo.service.ConfigService;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.text.SimpleDateFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 图像处理控制器类
 * 提供单帧图像识别、多帧图像识别、图像获取、特征数据获取和配置管理等功能。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:8080")
public class ImgController {

    private static final Logger logger = LoggerFactory.getLogger(ImgController.class);

    private final ImgProcessorCpp singleFrameProcessor;
    private final MultiFrameProcessorCpp multiFrameProcessor;
    private final FeatureParserService featureParserService;
    private final ConfigService configService;

    @Autowired
    public ImgController(ImgProcessorCpp singleFrameProcessor,
                         MultiFrameProcessorCpp multiFrameProcessor,
                         FeatureParserService featureParserService,
                         ConfigService configService) {
        this.singleFrameProcessor = singleFrameProcessor;
        this.multiFrameProcessor = multiFrameProcessor;
        this.featureParserService = featureParserService;
        this.configService = configService;
    }

    /**
     * 单帧图像识别接口。
     * @param file 上传的图像文件
     * @param algorithm 使用的算法
     * @param rows 图像行数
     * @param cols 图像列数
     * @param cropDataJson 裁剪坐标 JSON 字符串
     * @param fileMD5FromFrontend 前端提供的文件 MD5 校验值
     * @return 处理结果
     * @throws IOException 文件操作异常
     * @throws NoSuchAlgorithmException 无效算法异常
     */
    @PostMapping("/infer")
    public ResponseEntity<Map<String, Object>> inferImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam("algorithm") String algorithm,
            @RequestParam(value = "rows", required = true) int rows,
            @RequestParam(value = "cols", required = true) int cols,
            @RequestParam(value = "cropData", required = false) String cropDataJson,
            @RequestParam("fileMD5") String fileMD5FromFrontend) throws IOException, NoSuchAlgorithmException {

        logger.info("单帧识别请求: 文件: {}, 算法: {}, 行列: {}x{}, crop: {}",
                file.getOriginalFilename(), algorithm, rows, cols, cropDataJson != null);
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("必须提供有效的图像行数和列数。");
        }

        String backendGeneratedMD5 = calculateMD5(file.getBytes());
        if (!fileMD5FromFrontend.equals(backendGeneratedMD5)) {
            throw new IllegalArgumentException("MD5 校验失败。");
        }
        logger.info("MD5校验成功.");

        String originalFilename = file.getOriginalFilename();
        ConvertDatToImg.ConvertResult conversionResult = ConvertDatToImg.convertToPngBase64(file.getBytes(), originalFilename, rows, cols);
        if (conversionResult == null || conversionResult.normalizedBase64 == null) {
            throw new RuntimeException("文件转换处理失败。可能由于行列数与.dat文件不匹配。");
        }

        String originalBase64ForCpp = conversionResult.normalizedBase64;
        String processedBase64ForCpp = originalBase64ForCpp;

        Map<String, Integer> cropCoordinates = ParseCoord.parse(cropDataJson);
        if (cropCoordinates != null && !cropCoordinates.isEmpty()) {
            logger.info("进行图像裁剪: {}", cropCoordinates);
            processedBase64ForCpp = CropImg.cropImage(originalBase64ForCpp, cropCoordinates);
        }

        logger.info("调用服务进行单帧处理，算法: {}", algorithm);
        ImgProcessorCpp.SingleFrameResult result = singleFrameProcessor.processImage(originalBase64ForCpp, processedBase64ForCpp, cropCoordinates, algorithm);

        // 成功逻辑保持不变，因为如果 processImage 失败，它会抛出异常，根本不会执行到这里
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", true);
        responseMap.put("processedImage", result.getProcessedBase64());
        responseMap.put("algorithm", algorithm);
        responseMap.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        responseMap.put("result", result.getResultArray());
        responseMap.put("result_length", result.getResultLength());
        responseMap.put("message", result.getMessage() != null ? result.getMessage() : "处理成功");

        logger.info("单帧识别请求处理完成: {}", originalFilename);
        return ResponseEntity.ok(responseMap);
    }

    /**
     * 多帧图像识别接口（基于文件夹路径）。
     * @param requestBody 文件夹路径请求体
     * @return 处理结果
     * @throws IOException 文件操作异常
     */
    @PostMapping("/infer_folder_path")
    public ResponseEntity<MultiFrameResultResponse> handleMultiFrameFolderInference(@RequestBody FolderPathRequest requestBody) throws IOException {
        String folderPath = requestBody.getFolderPath();
        String algorithm = requestBody.getAlgorithm();

        logger.info("多帧识别请求 (路径模式): 文件夹: {}, 算法: {}", folderPath, algorithm);

        if (folderPath == null || folderPath.trim().isEmpty() ||
                algorithm == null || algorithm.trim().isEmpty()) {
            throw new IllegalArgumentException("folderPath 和 algorithm 参数不能为空");
        }
        MultiFrameResultResponse result = multiFrameProcessor.processDirectory(folderPath, algorithm);

        logger.info("多帧识别成功，结果输出目录: {}", result.getResultPath());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取图像文件接口。
     * @param folder 图像所在文件夹路径
     * @param file 图像文件名
     * @return 图像文件
     * @throws IOException 文件操作异常
     */
    @GetMapping("/get_image")
    public ResponseEntity<?> getImage(
            @RequestParam("folder") String folder,
            @RequestParam("file") String file) throws IOException {

        if (folder == null || folder.trim().isEmpty() || file == null || file.trim().isEmpty()) {
            throw new IllegalArgumentException("folder 和 file 参数不能为空。");
        }
        Path imagePath = Paths.get(folder, file);
        logger.debug("请求图像: {}", imagePath.toString());

        if (!Files.exists(imagePath) || !Files.isReadable(imagePath) || Files.isDirectory(imagePath)) {
            throw new java.io.FileNotFoundException("请求的图像文件不存在或不可读: " + imagePath);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(imagePath.toFile()));
        HttpHeaders headers = new HttpHeaders();

        String contentType = Files.probeContentType(imagePath);
        if (contentType == null) {
            if (file.toLowerCase().endsWith(".png")) contentType = MediaType.IMAGE_PNG_VALUE;
            else if (file.toLowerCase().endsWith(".jpg") || file.toLowerCase().endsWith(".jpeg")) contentType = MediaType.IMAGE_JPEG_VALUE;
            else contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        headers.add(HttpHeaders.CONTENT_TYPE, contentType);

        logger.info("提供图像文件: {}, Content-Type: {}", imagePath, contentType);
        return ResponseEntity.ok()
                .headers(headers)
                .contentLength(Files.size(imagePath))
                .body(resource);
    }

    /**
     * 获取特征数据接口。
     * @param resultPathArg 结果路径参数
     * @return 特征数据
     * @throws IOException 文件操作异常
     */
    @GetMapping("/get_feature_data")
    public ResponseEntity<FeatureDataResponse> getFeatureData(@RequestParam("resultPath") String resultPathArg) throws IOException {
        logger.info("--- 接收到的 resultPathArg: {} ---", resultPathArg);

        if (resultPathArg == null || resultPathArg.trim().isEmpty()) {
            throw new IllegalArgumentException("resultPath 参数不能为空。");
        }

        Path featureDatFileAbsolutePath;
        Path relativeResultPath = Paths.get(resultPathArg);
        featureDatFileAbsolutePath = Paths.get(".").resolve(relativeResultPath).resolve("Feature.dat").toAbsolutePath().normalize();

        logger.info("尝试读取和解析的特征文件绝对路径: {}", featureDatFileAbsolutePath.toString());

        if (!Files.exists(featureDatFileAbsolutePath) || !Files.isReadable(featureDatFileAbsolutePath)) {
            throw new java.io.FileNotFoundException("特征文件 (Feature.dat) 未找到或不可读。检查路径: " + featureDatFileAbsolutePath);
        }

        Map<String, List<? extends Number>> features = this.featureParserService.parseFeatureFile(featureDatFileAbsolutePath.toString());

        if (features == null || features.isEmpty()) {
            logger.warn("特征文件解析完成，但未提取到任何特征数据 (可能 numFrames <= 0)。 文件: {}", featureDatFileAbsolutePath.toString());
            return ResponseEntity.ok(
                    new FeatureDataResponse(true, "特征文件已处理，但未包含有效数据帧或特征。", new HashMap<>())
            );
        }

        logger.info("成功提取特征数据，共 {} 个特征类型。", features.size());
        return ResponseEntity.ok(
                new FeatureDataResponse(true, "特征数据提取成功。", features)
        );
    }

    /**
     * 获取配置接口。
     * @return 配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<ConfigDto> getConfig() {
        logger.info("收到获取配置请求.");
        ConfigDto config = configService.getConfig();
        logger.info("成功获取配置: {}", config);
        return ResponseEntity.ok(config);
    }

    /**
     * 保存配置接口。
     * @param configDto 配置数据传输对象
     * @return 空响应
     */
    @PostMapping("/config")
    public ResponseEntity<Void> saveConfig(@RequestBody ConfigDto configDto) {
        logger.info("收到保存配置请求: {}", configDto);
        configService.saveConfig(configDto);
        logger.info("配置保存成功.");
        return ResponseEntity.ok().build();
    }

    /**
     * 计算文件的 MD5 校验值。
     * @param fileBytes 文件字节数组
     * @return MD5 校验值
     * @throws NoSuchAlgorithmException 无效算法异常
     */
    private String calculateMD5(byte[] fileBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(fileBytes);
        return HexFormat.of().formatHex(digest);
    }
}