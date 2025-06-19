package com.demo.imgProcess.dto;

public class FeatureDefinition {
    private String name;
    private char typeChar; // 'f' 代表 float, 'i' 代表 int

    public FeatureDefinition() {
    }

    public FeatureDefinition(String name, char typeChar) {
        this.name = name;
        this.typeChar = typeChar;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public char getTypeChar() {
        return typeChar;
    }

    public void setTypeChar(char typeChar) {
        this.typeChar = typeChar;
    }

    public int getTypeSize() {
        if (typeChar == 'f' || typeChar == 'i') {
            return 4;
        }
        throw new IllegalArgumentException("不支持的类型字符: " + typeChar);
    }
}