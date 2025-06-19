package com.demo.imgProcess;


public interface ConfigService {
    int getCropX();
    int getCropY();
    int getCropWidth();
    int getCropHeight();
    double getLearningRate();
    void updateCropConfig(int x, int y, int width, int height,double lr);
}