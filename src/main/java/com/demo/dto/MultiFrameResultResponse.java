package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 多帧结果响应类
 * 用于封装多帧处理结果的响应DTO（Data Transfer Object）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiFrameResultResponse {
    private boolean success;//表示处理是否成功。
    private String resultPath;//处理结果文件的存储路径。
    private ResultFiles resultFiles;//封装原始文件名、感兴趣图像名和输出图像名的嵌套对象。
    private String message;//处理过程中返回的消息.
    private Integer fileNumProcessed;//已成功处理的文件数量

    /**
     * 用于存储文件相关信息的内部类。
     * 封装了原始文件名、感兴趣图像名和输出图像名的列表。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultFiles {
        private List<String> originalNames;
        private List<String> interestImageNames;
        private List<String> outputImageNames;
    }
}