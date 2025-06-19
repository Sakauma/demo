package com.demo.imgProcess;

import com.demo.imgProcess.Config;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConfigRepository extends JpaRepository<Config, Long> {
    Optional<Config> findByKey(String key);

    @Query("SELECT c FROM Config c WHERE c.key LIKE 'crop.%'")
    List<Config> findCropConfigs();
}
