package com.techsutra.intruderalert.repository;

import com.techsutra.intruderalert.entity.AuthorizedFaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizedFaceRepository extends JpaRepository<AuthorizedFaceEntity, Long> {
    Optional<AuthorizedFaceEntity> findByUserId(Long userId);
}
