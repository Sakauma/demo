package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 特征定义类
 * 包含特征名称和类型字符，并提供类型大小的计算方法。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDefinition {
    private String name;
    private char typeChar;

    /**
     * 根据类型字符返回该类型在内存中的大小（以字节为单位）。
     * @return 类型的大小，目前 'f' 和 'i' 返回 4 字节。
     * @throws IllegalArgumentException 如果类型字符不支持，抛出异常。
     */
    public int getTypeSize() {
        if (typeChar == 'f' || typeChar == 'i') {
            return 4;
        }
        throw new IllegalArgumentException("不支持的类型字符: " + typeChar);
    }
}