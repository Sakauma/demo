package com.demo.repository;

import com.demo.entity.FrameFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * FrameFeature (每帧特征) 的数据访问仓库接口
 */
@Repository
public interface FrameFeatureRepository extends JpaRepository<FrameFeature, Long> {}