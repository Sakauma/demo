package com.demo.config;

import com.demo.dto.FeatureDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.features")
public class FeatureProperties {

    private List<FeatureDefinition> definitions;

    public List<FeatureDefinition> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<FeatureDefinition> definitions) {
        this.definitions = definitions;
    }
}