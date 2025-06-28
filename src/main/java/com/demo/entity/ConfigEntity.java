package com.demo.entity;
import javax.persistence.*;
import lombok.Data;

/**
 * 配置实体类，用于存储应用程序的全局配置信息。
 * 对应数据库中的 app_config 表。
 */
@Data
@Entity
@Table(name = "app_config")
public class ConfigEntity {
    @Id
    private Long id = 1L;//主键 ID，固定为 1，确保全局唯一配置

    private int regionX;
    private int regionY;
    private int regionWidth;
    private int regionHeight;
    private double algorithmLr;
}