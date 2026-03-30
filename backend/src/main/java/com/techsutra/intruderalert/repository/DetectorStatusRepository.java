package com.techsutra.intruderalert.repository;

import com.techsutra.intruderalert.entity.DetectorStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DetectorStatusRepository extends JpaRepository<DetectorStatusEntity, Long> {
    Optional<DetectorStatusEntity> findTopByOrderByIdDesc();
}
