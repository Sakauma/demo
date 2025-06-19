package com.demo.imgProcess;
import com.demo.imgProcess.dto.ConfigDto;

import com.utils.ConvertDatToImg;
import com.utils.CropImg;
import com.utils.ParseCoord;
import com.demo.imgProcess.dto.FolderPathRequest;
import com.demo.imgProcess.dto.MultiFrameResultResponse;
import com.demo.imgProcess.dto.FeatureDataResponse;
import com.demo.imgProcess.dto.FeatureParserService;
import com.demo.imgProcess.service.ConfigService;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.text.SimpleDateFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

    @PostMapping("/infer")
    public ResponseEntity<Map<String, Object>> inferImage(
            @RequestPart("file") MultipartFile file,
            @RequestParam("algorithm") String algorithm,
            @RequestParam(value = "rows", required = true) int rows,
            @RequestParam(value = "cols", required = true) int cols,
            @RequestParam(value = "cropData", required = false) String cropDataJson,
            @RequestParam("fileMD5") String fileMD5FromFrontend) {

        logger.info("单帧识别请求: 文件: {}, 算法: {}, 行列: {}x{}, crop: {}",
                file.getOriginalFilename(), algorithm, rows, cols, cropDataJson != null);
        Map<String, Object> responseMap = new HashMap<>();

        if (rows <= 0 || cols <= 0) {
            logger.warn("无效的行列数: rows={}, cols={}", rows, cols);
            responseMap.put("success", false);
            responseMap.put("error", "必须提供有效的图像行数和列数。");
            return ResponseEntity.badRequest().body(responseMap);
        }

        try {
            String backendGeneratedMD5 = calculateMD5(file.getBytes());
            if (!fileMD5FromFrontend.equals(backendGeneratedMD5)) {
                logger.warn("MD5校验失败. 前端: {}, 后端: {}", fileMD5FromFrontend, backendGeneratedMD5);
                responseMap.put("success", false);
                responseMap.put("error", "MD5 校验失败。");
                return ResponseEntity.badRequest().body(responseMap);
            }
            logger.info("MD5校验成功.");

            String originalFilename = file.getOriginalFilename();
            ConvertDatToImg.ConvertResult conversionResult = ConvertDatToImg.convertToPngBase64(file.getBytes(), originalFilename, rows, cols);
            if (conversionResult == null || conversionResult.normalizedBase64 == null) {
                logger.error("文件转换为 Base64 PNG 失败: {}", originalFilename);
                responseMap.put("success", false);
                responseMap.put("error", "文件转换处理失败。可能由于行列数与.dat文件不匹配。");
                return ResponseEntity.badRequest().body(responseMap);
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

            if (result.isSuccess()) {
                responseMap.put("success", true);
                responseMap.put("processedImage", result.getProcessedBase64());
                responseMap.put("algorithm", algorithm);
                responseMap.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                responseMap.put("result", result.getResultArray());
                responseMap.put("result_length", result.getResultLength());
                responseMap.put("message", result.getMessage() != null ? result.getMessage() : "处理成功");

                logger.info("单帧识别请求处理完成: {}", originalFilename);
                return ResponseEntity.ok(responseMap);
            } else {
                // 如果处理失败，从 DTO 中获取错误信息
                responseMap.put("success", false);
                responseMap.put("error", result.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseMap);
            }

        } catch (Exception e) { // 这个 catch 块现在只处理 Controller 层的异常
            logger.error("单帧处理时发生未知错误: {}", e.getMessage(), e);
            responseMap.put("success", false);
            responseMap.put("error", "服务器内部未知错误。");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(responseMap);
        }
    }

    @PostMapping("/infer_folder_path")
    public ResponseEntity<MultiFrameResultResponse> handleMultiFrameFolderInference(@RequestBody FolderPathRequest requestBody) {
        String folderPath = requestBody.getFolderPath();
        String algorithm = requestBody.getAlgorithm();

        logger.info("多帧识别请求 (路径模式): 文件夹: {}, 算法: {}", folderPath, algorithm);

        if (folderPath == null || folderPath.trim().isEmpty() ||
                algorithm == null || algorithm.trim().isEmpty()) {
            logger.warn("多帧请求参数无效: folderPath或algorithm为空");
            return ResponseEntity.badRequest().body(
                    new MultiFrameResultResponse(false, null, null, "folderPath 和 algorithm 参数不能为空", null)
            );
        }

        try {
            MultiFrameResultResponse result = multiFrameProcessor.processDirectory(folderPath, algorithm);

            if (result != null && result.isSuccess()) {
                logger.info("多帧识别成功，结果输出目录: {}", result.getResultPath());
                return ResponseEntity.ok(result); //直接返回DTO
            } else {
                String errorMessage = (result != null && result.getMessage() != null) ? result.getMessage() : "核心处理模块未能成功生成结果或处理失败。";
                logger.error("多帧识别处理失败: {}", errorMessage);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        new MultiFrameResultResponse(false, null, null, errorMessage, null)
                );
            }
        } catch (UnsatisfiedLinkError ule) {
            logger.error("JNA链接错误 (调用MultiFrameProcessorService时): {}", ule.getMessage(), ule);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new MultiFrameResultResponse(false, null, null, "无法链接到多帧核心处理库: " + ule.getMessage(), null)
            );
        }
        catch (RuntimeException e) {
            logger.error("多帧处理业务逻辑错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new MultiFrameResultResponse(false, null, null, "多帧处理失败: " + e.getMessage(), null)
            );
        }
        catch (Exception e) { // 更通用的捕获
            logger.error("处理多帧识别请求时发生未知内部错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new MultiFrameResultResponse(false, null, null, "服务器内部未知错误: " + e.getMessage(), null)
            );
        }
    }

    @GetMapping("/get_image")
    public ResponseEntity<?> getImage(
            @RequestParam("folder") String folder,
            @RequestParam("file") String file) {

        if (folder == null || folder.trim().isEmpty() || file == null || file.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Missing folder or file parameter.");
        }
        Path imagePath = Paths.get(folder, file);
        logger.debug("请求图像: {}", imagePath.toString());

        if (!Files.exists(imagePath) || !Files.isReadable(imagePath) || Files.isDirectory(imagePath)) {
            logger.warn("请求的图像文件不存在或不可读: {}", imagePath);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Image not found or not accessible.");
        }

        try {
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

        } catch (IOException e) {
            logger.error("读取图像文件时出错: {}", imagePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading image file.");
        }
    }

    // ImgController.java
    @GetMapping("/get_feature_data")
    public ResponseEntity<FeatureDataResponse> getFeatureData(@RequestParam("resultPath") String resultPathArg) { // DTO类名featureDataResponse应为FeatureDataResponse
        logger.info("--- /api/get_feature_data 端点被命中! 接收到的 resultPathArg: {} ---", resultPathArg); // 确认端点是否被命中

        if (resultPathArg == null || resultPathArg.trim().isEmpty()) {
            logger.warn("resultPath 参数为空或无效。");
            return ResponseEntity.badRequest().body(
                    new FeatureDataResponse(false, "resultPath 参数不能为空。", null)
            );
        }

        try {
            Path featureDatFileAbsolutePath;
            Path relativeResultPath = Paths.get(resultPathArg);

            // 假设 relativeResultPath (如 "../result/") 是相对于当前工作目录的
            featureDatFileAbsolutePath = Paths.get(".").resolve(relativeResultPath).resolve("Feature.dat").toAbsolutePath().normalize();

            logger.info("尝试读取和解析的特征文件绝对路径: {}", featureDatFileAbsolutePath.toString());

            if (!Files.exists(featureDatFileAbsolutePath) || !Files.isReadable(featureDatFileAbsolutePath)) {
                String msg = "特征文件 (Feature.dat) 未找到或不可读。检查路径: " + featureDatFileAbsolutePath.toString();
                logger.warn(msg);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        new FeatureDataResponse(false, msg, null)
                );
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

        } catch (IOException e) {
            String msg = "读取或解析特征文件时发生IO错误: " + e.getMessage();
            logger.error(msg, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new FeatureDataResponse(false, msg, null)
            );
        } catch (Exception e) { // 捕获其他潜在的运行时异常，例如来自 featureParserSer.parseFeatureFile 的
            String msg = "处理特征数据请求时发生未知错误: " + e.getMessage();
            logger.error(msg, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new FeatureDataResponse(false, msg, null)
            );
        }
    }

    @GetMapping("/config")
    public ResponseEntity<ConfigDto> getConfig() {
        return ResponseEntity.ok(configService.getConfig());
    }

    @PostMapping("/config")
    public ResponseEntity<Void> saveConfig(@RequestBody ConfigDto configDto) {
        configService.saveConfig(configDto);
        return ResponseEntity.ok().build();
    }

    private String calculateMD5(byte[] fileBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(fileBytes);
        return HexFormat.of().formatHex(digest);
    }
}