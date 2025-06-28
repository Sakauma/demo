package com.demo.config;

import lombok.Getter;
import lombok.Setter;
import java.util.List;
import com.demo.dto.FeatureDefinition;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能特性配置类。
 * 这个类作为一个类型安全的配置属性持有者，用于从Spring Boot的外部配置文件
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureProperties {
    private List<FeatureDefinition> definitions;
}