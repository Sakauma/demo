package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultiFrameResultResponse {
    private boolean success;
    private String resultPath;
    private ResultFiles resultFiles;
    private String message;
    private Integer fileNumProcessed;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultFiles {
        private List<String> originalNames;
        private List<String> interestImageNames;
        private List<String> outputImageNames;
    }
}