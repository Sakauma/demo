-- 确保使用正确的表结构和列名
INSERT INTO app_config (config_key, config_value, description) VALUES
                                                                   ('crop.x', '0', '裁剪区域左上角X坐标'),
                                                                   ('crop.y', '0', '裁剪区域左上角Y坐标'),
                                                                   ('crop.width', '320', '裁剪区域宽度'),
                                                                   ('crop.height', '240', '裁剪区域高度'),
                                                                   ('lr', '0.0001', '学习率配置')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), description = VALUES(description);