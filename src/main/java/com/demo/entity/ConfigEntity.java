package com.demo.entity;
import javax.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "app_config")
public class ConfigEntity {
    @Id
    private Long id = 1L;

    private int regionX;
    private int regionY;
    private int regionWidth;
    private int regionHeight;
    private double algorithmLr;
}