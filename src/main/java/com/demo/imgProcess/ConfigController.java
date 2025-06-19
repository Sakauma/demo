package com.demo.imgProcess;

import com.demo.imgProcess.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:8080")
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;

    @Autowired
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/crop")
    public Map<String, Object> getCropConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("x", configService.getCropX());
        config.put("y", configService.getCropY());
        config.put("width", configService.getCropWidth());
        config.put("height", configService.getCropHeight());
        config.put("lr",configService.getLearningRate());
        // 直接存储 float/double 值
        return config;
    }


    @PutMapping("/crop")


    public ResponseEntity<Void> updateCropConfig(@RequestBody Map<String, Number> config) {
        configService.updateCropConfig(
                config.getOrDefault("x", 0).intValue(),
                config.getOrDefault("y", 0).intValue(),
                config.getOrDefault("width", 100).intValue(),
                config.getOrDefault("height", 100).intValue(),
                config.getOrDefault("lr", 0.0001).doubleValue() // 注意float转换
        );
        return ResponseEntity.ok().build();
    }
}