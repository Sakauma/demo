CREATE TABLE IF NOT EXISTS app_config (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          config_key VARCHAR(100) NOT NULL UNIQUE,
                                          config_value VARCHAR(10) NOT NULL,
                                          description VARCHAR(255),
                                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);