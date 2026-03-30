package com.techsutra.intruderalert.repository;

import com.techsutra.intruderalert.entity.SystemSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingsRepository extends JpaRepository<SystemSettingsEntity, Long> {
}
