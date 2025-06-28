package com.demo.dto;

import lombok.Data;

/**
 *文件夹路径请求类
 */
@Data
public class FolderPathRequest {
    private String folderPath;//文件夹路径
    private String algorithm;//算法名称
}