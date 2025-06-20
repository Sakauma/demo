//package com.demo.imgProcess.dto;
//
//import java.util.List;
//import java.util.Map;
//
//public class FeatureDataResponse {
//    private boolean success;
//    private String message;
//    // 使用通配符以支持 List<Float> 或 List<Integer>
//    private Map<String, List<? extends Number>> features;
//
//    // 构造函数
//    public FeatureDataResponse(boolean success, String message, Map<String, List<? extends Number>> features) {
//        this.success = success;
//        this.message = message;
//        this.features = features;
//    }
//
//    // Getters 和 Setters
//    public boolean isSuccess() {
//        return success;
//    }
//
//    public void setSuccess(boolean success) {
//        this.success = success;
//    }
//
//    public String getMessage() {
//        return message;
//    }
//
//    public void setMessage(String message) {
//        this.message = message;
//    }
//
//    public Map<String, List<? extends Number>> getFeatures() {
//        return features;
//    }
//
//    public void setFeatures(Map<String, List<? extends Number>> features) {
//        this.features = features;
//    }
//}

package com.demo.imgProcess.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDataResponse {
    private boolean success;
    private String message;
    private Map<String, List<? extends Number>> features;
}