// src/main/java/com/demo/imgProcess/dto/FolderPathRequest.java
package com.demo.imgProcess.dto;

public class FolderPathRequest {
    private String folderPath;
    private String algorithm;
    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    // public int getRows() { return rows; }
    // public void setRows(int rows) { this.rows = rows; }
    // public int getCols() { return cols; }
    // public void setCols(int cols) { this.cols = cols; }

    @Override
    public String toString() {
        return "FolderPathRequest{" +
                "folderPath='" + folderPath + '\'' +
                ", algorithm='" + algorithm + '\'' +
                // ", rows=" + rows +
                // ", cols=" + cols +
                '}';
    }
}