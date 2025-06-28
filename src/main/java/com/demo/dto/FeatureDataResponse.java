package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 特征数据响应类
 * 用于封装特征数据的查询结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDataResponse {
    private boolean success;// 是否成功标志
    private String message; // 消息内容
    private Map<String, List<? extends Number>> features;// 特征数据，以Map形式存储，键为特征名称，值为特征值的列表（可以是任何数字类型）
}
