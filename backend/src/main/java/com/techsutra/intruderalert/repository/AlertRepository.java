package com.techsutra.intruderalert.repository;

import com.techsutra.intruderalert.entity.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<AlertEntity, Long> {
}
