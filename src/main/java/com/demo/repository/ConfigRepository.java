package com.demo.repository;

import com.demo.entity.ConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 配置数据访问仓库接口
 * 继承JpaRepository以提供对ConfigEntity的CRUD操作。
 */
@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntity, Long> {}