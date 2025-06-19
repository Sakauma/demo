package com.demo.imgProcess;

import javax.persistence.*;

@Entity
@Table(name = "app_config")
public class Config {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", unique = true, nullable = false, length = 100)
    private String key;

    @Column(name = "config_value", nullable = false, length = 255)
    private String value;

    @Column(name = "description", length = 255)
    private String description;

    public Config() {
    }

    public Config(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}