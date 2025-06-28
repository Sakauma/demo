package com.demo.exception;

import com.demo.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 全局异常处理器类
 * 用于捕获和处理Spring Boot应用程序中抛出的各种异常
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 捕获自定义的库处理异常
    @ExceptionHandler(ProcessException.class)
    public ResponseEntity<ErrorResponse> handleNativeProcessException(ProcessException ex, WebRequest request) {
        String path = ((ServletWebRequest)request).getRequest().getRequestURI();
        logger.error("核心库处理时发生错误 at path {}: {}", path, ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Core Processing Error",
                ex.getMessage(),
                path
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 捕获参数校验等失败的异常
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        String path = ((ServletWebRequest)request).getRequest().getRequestURI();
        logger.warn("请求参数无效 at path {}: {}", path, ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Argument",
                ex.getMessage(),
                path
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // 处理文件读写相关的 IO 异常
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException ex, WebRequest request) {
        String path = ((ServletWebRequest)request).getRequest().getRequestURI();
        // 对于文件未找到的特定情况，可以返回 404
        if (ex instanceof java.io.FileNotFoundException) {
            logger.warn("请求的文件未找到 at path {}: {}", path, ex.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(
                    LocalDateTime.now(),
                    HttpStatus.NOT_FOUND.value(),
                    "Not Found",
                    ex.getMessage(),
                    path
            );
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }
        // 其他 I/O 错误返回 500
        logger.error("发生 I/O 错误 at path {}: {}", path, ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "I/O Error",
                "文件读写失败: " + ex.getMessage(),
                path
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 通用的异常处理
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        String path = ((ServletWebRequest)request).getRequest().getRequestURI();
        logger.error("发生未知服务内部错误 at path {}: {}", path, ex.getMessage(), ex);
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "服务发生未知错误，请联系管理员。",
                path
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}