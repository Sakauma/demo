package com.demo.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            // 自定义Tomcat连接器
            factory.addConnectorCustomizers(connector -> {
                if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol) {
                    AbstractHttp11Protocol<?> protocolHandler = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();

                    // 关键设置：设置POST请求体的最大大小（包括所有部分）
                    // 这等同于 spring.servlet.multipart.max-request-size
                    // 设置为 -1 表示无限制，或者设置为一个较大的值，例如 1024 * 1024 * 1024 (1GB)
                    protocolHandler.setMaxSwallowSize(-1);

                    // 关键设置：设置HTTP请求头的最大数量
                    // 有时文件数量过多也会导致请求头数量增加
                    protocolHandler.setMaxHeaderCount(50000);
                }
            });
        };
    }
}