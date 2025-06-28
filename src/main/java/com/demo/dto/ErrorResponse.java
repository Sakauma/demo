package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 错误响应格式
 * 用于封装API调用过程中发生的错误信息，统一返回给客户端
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private LocalDateTime timestamp; // 错误发生的时间戳
    private int status; // HTTP状态码
    private String error; // 错误类型描述
    private String message; // 错误的详细描述信息
    private String path; // 发生错误的请求路径
}