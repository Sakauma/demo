package com.demo.imgProcess.entity;
import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "app_config")
public class ConfigEntity {
    @Id
    private Long id = 1L; // 我们只存一条记录，ID 固定为 1

    private int regionX;
    private int regionY;
    private int regionWidth;
    private int regionHeight;
    private double algorithmLr;
}