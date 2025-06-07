package com.demo.imgProcess.dto;

import java.util.List;

public class MultiFrameResultResponse {
    private boolean success;
    private String resultPath;
    private ResultFiles resultFiles;
    private String message;
    private Integer fileNumProcessed;


    public static class ResultFiles {
        private List<String> originalNames;
        private List<String> interestImageNames;
        private List<String> outputImageNames;

        public ResultFiles(List<String> originalNames, List<String> interestImageNames, List<String> outputImageNames) {
            this.originalNames = originalNames;
            this.interestImageNames = interestImageNames;
            this.outputImageNames = outputImageNames;
        }
        // Getters
        public List<String> getOriginalNames() { return originalNames; }
        public List<String> getInterestImageNames() { return interestImageNames; }
        public List<String> getOutputImageNames() { return outputImageNames; }
        // Setters if needed
    }

    public MultiFrameResultResponse(boolean success, String resultPath, ResultFiles resultFiles, String message, Integer fileNumProcessed) {
        this.success = success;
        this.resultPath = resultPath;
        this.resultFiles = resultFiles;
        this.message = message;
        this.fileNumProcessed = fileNumProcessed;
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getResultPath() { return resultPath; }
    public ResultFiles getResultFiles() { return resultFiles; }
    public String getMessage() { return message; }
    public Integer getFileNumProcessed() { return fileNumProcessed; }
}