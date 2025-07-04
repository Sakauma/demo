package com.demo.service;

import com.demo.dto.ConfigDto;
import com.demo.entity.ConfigEntity;
import com.demo.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Optional;

/**
 * 配置数据服务类
 * 实现获取配置，保存配置，从 INI 文件读取配置，更新 INI 文件，实体与 DTO 映射
 */
@Service
@RequiredArgsConstructor
public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private final ConfigRepository configRepository;

    @Value("${app.config.ini-path}")
    private String iniFilePath;
    private static final Long CONFIG_ID = 1L;

    /**
     * 获取配置信息，优先从数据库读取，如果数据库没有则从本地 INI 文件读取。
     *
     * @return ConfigDto 配置数据传输对象
     */
    public synchronized ConfigDto getConfig() {
        // 优先从数据库读取
        logger.debug("尝试从数据库加载配置 (ID: {}).", CONFIG_ID);
        Optional<ConfigEntity> entityOptional = configRepository.findById(CONFIG_ID);
        if (entityOptional.isPresent()) {
            logger.info("从数据库加载配置。");
            return mapEntityToDto(entityOptional.get());
        } else {
            // 如果数据库为空，则从本地 data.ini 文件读取
            logger.info("数据库无配置，从 {} 文件加载默认配置。", iniFilePath);
            return readFromIniFile();
        }
    }

    /**
     * 保存配置到数据库，并同步更新本地 INI 文件。
     *
     * @param dto 配置数据传输对象
     */
    @Transactional
    public synchronized void saveConfig(ConfigDto dto) {
        logger.info("准备保存配置到数据库. DTO: {}", dto);
        ConfigEntity entity = configRepository.findById(CONFIG_ID).orElse(new ConfigEntity());
        entity.setId(CONFIG_ID);
        // 更新 Entity
        entity.setRegionX(dto.getRegion().getX());
        entity.setRegionY(dto.getRegion().getY());
        entity.setRegionWidth(dto.getRegion().getWidth());
        entity.setRegionHeight(dto.getRegion().getHeight());
        entity.setAlgorithmLr(dto.getAlgorithm().getLr());
        // 保存到数据库
        configRepository.save(entity);
        logger.info("配置已保存到数据库。");

        // 立即更新本地 .ini 文件
        updateIniFile(entity);
    }

    /**
     * 从本地 INI 文件读取配置。
     *
     * @return ConfigDto 配置数据传输对象
     * @throws IOException 如果文件不存在或解析失败
     */
    private ConfigDto readFromIniFile() {
        try {
            File iniFile = new File(iniFilePath);
            if (!iniFile.exists()) {
                logger.error("INI 文件未找到: {}", iniFilePath);
                throw new IOException("INI file not found at " + iniFilePath); //
            }

            Ini ini = new Ini(new FileReader(iniFile));
            Ini.Section region = ini.get("Region");
            Ini.Section algorithm = ini.get("ALGORITHM");

            if (region == null || algorithm == null) {
                logger.error("INI 文件 {} 中缺少必要的 [Region] 或 [ALGORITHM] 部分.", iniFilePath);
                throw new IOException("Missing required sections [Region] or [ALGORITHM] in INI file.");
            }

            ConfigDto dto = new ConfigDto();
            dto.setRegion(new ConfigDto.Region());
            dto.setAlgorithm(new ConfigDto.Algorithm());

            dto.getRegion().setX(region.get("x", int.class));
            dto.getRegion().setY(region.get("y", int.class));
            dto.getRegion().setWidth(region.get("width", int.class));
            dto.getRegion().setHeight(region.get("height", int.class));
            dto.getAlgorithm().setLr(algorithm.get("lr", double.class));

            return dto;
        } catch (IOException e) { //
            logger.error("读取 .ini 配置文件 {} 失败: {}", iniFilePath, e.getMessage(), e);
            throw new RuntimeException("读取配置文件失败", e);
        } catch (Exception e) {
            logger.error("解析 .ini 配置文件 {} 时发生错误: {}", iniFilePath, e.getMessage(), e);
            throw new RuntimeException("解析配置文件失败", e);
        }
    }

    /**
     * 更新本地 INI 文件中的配置。
     *
     * @param entity 配置实体对象
     * @throws IOException 如果文件更新失败
     */
    private void updateIniFile(ConfigEntity entity) {
        try {
            File iniFile = new File(iniFilePath);
            Ini ini = new Ini();
            // 如果文件存在，先加载
            if (iniFile.exists()) {
                ini.load(iniFile);
            }
            // 更新值
            ini.put("Region", "x", entity.getRegionX());
            ini.put("Region", "y", entity.getRegionY());
            ini.put("Region", "width", entity.getRegionWidth());
            ini.put("Region", "height", entity.getRegionHeight());
            ini.put("ALGORITHM", "lr", entity.getAlgorithmLr());
            // 保存到文件
            ini.store(iniFile);
            logger.info("本地配置文件 {} 已更新。", iniFilePath);
        } catch (IOException e) {
            logger.error("更新 .ini 配置文件失败: {}", e.getMessage());
            throw new RuntimeException("更新配置文件失败", e);
        }
    }

    /**
     * 将配置实体对象映射为数据传输对象。
     *
     * @param entity 配置实体对象
     * @return ConfigDto 配置数据传输对象
     */
    private ConfigDto mapEntityToDto(ConfigEntity entity) {
        ConfigDto dto = new ConfigDto();
        dto.setRegion(new ConfigDto.Region());
        dto.setAlgorithm(new ConfigDto.Algorithm());
        dto.getRegion().setX(entity.getRegionX());
        dto.getRegion().setY(entity.getRegionY());
        dto.getRegion().setWidth(entity.getRegionWidth());
        dto.getRegion().setHeight(entity.getRegionHeight());
        dto.getAlgorithm().setLr(entity.getAlgorithmLr());
        return dto;
    }
}