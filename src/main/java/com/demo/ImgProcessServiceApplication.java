package com.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ImgProcessServiceApplication 是 Spring Boot 应用的主入口类。
 * 该类使用 @SpringBootApplication 注解，表示这是一个 Spring Boot 应用程序。
 * @SpringBootApplication 是一个组合注解，它包含了以下注解：
 * - @Configuration: 表示该类是一个配置类，用于定义 Spring 的配置。
 * - @EnableAutoConfiguration: 启用 Spring Boot 的自动配置功能。
 * - @ComponentScan: 启用组件扫描，自动扫描当前包及其子包下的组件。
 */
@SpringBootApplication
public class ImgProcessServiceApplication {

    /**
     * main 方法是 Java 应用的入口点。
     * SpringApplication.run() 方法用于启动 Spring Boot 应用程序。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ImgProcessServiceApplication.class, args);
    }
}