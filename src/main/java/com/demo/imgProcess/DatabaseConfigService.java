package com.demo.imgProcess;


import com.demo.imgProcess.Config;
import com.demo.imgProcess.ConfigRepository;
import com.demo.imgProcess.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class DatabaseConfigService implements ConfigService {

    private final ConfigRepository configRepository;

    @Autowired
    public DatabaseConfigService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public int getCropX() {
        return getIntValue("crop.x", 0);
    }

    @Override
    public int getCropY() {
        return getIntValue("crop.y", 0);
    }

    @Override
    public int getCropWidth() {
        return getIntValue("crop.width", 100);
    }

    @Override
    public int getCropHeight() {
        return getIntValue("crop.height", 100);
    }

    public double getLearningRate() {
        return getDoubleValue("lr",0.0001);
    }


    private int getIntValue(String key, int defaultValue) {
        Optional<Config> config = configRepository.findByKey(key);
        if (config.isPresent()) {
            try {
                return Integer.parseInt(config.get().getValue());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double getDoubleValue(String key, double defaultValue) {
        Optional<Config> config = configRepository.findByKey(key);
        if (config.isPresent()) {
            try {
                return Double.parseDouble(config.get().getValue());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    @Override
    @Transactional
    public void updateCropConfig(int x, int y, int width, int height,double lr) {
        updateConfig("crop.x", String.valueOf(x), "裁剪区域X坐标");
        updateConfig("crop.y", String.valueOf(y), "裁剪区域Y坐标");
        updateConfig("crop.width", String.valueOf(width), "裁剪区域宽度");
        updateConfig("crop.height", String.valueOf(height), "裁剪区域高度");
        updateConfig("lr",String.valueOf(lr),"学习率配置");
    }

    private void updateConfig(String key, String value, String description) {
        Config config = configRepository.findByKey(key)
                .orElseGet(() -> new Config(key, value, description));
        config.setValue(value);
        config.setDescription(description);
        configRepository.save(config);
    }
}