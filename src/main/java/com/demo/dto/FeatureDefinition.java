package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDefinition {
    private String name;
    private char typeChar;

    public int getTypeSize() {
        if (typeChar == 'f' || typeChar == 'i') {
            return 4;
        }
        throw new IllegalArgumentException("不支持的类型字符: " + typeChar);
    }
}