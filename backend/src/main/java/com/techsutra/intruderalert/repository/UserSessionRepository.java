package com.techsutra.intruderalert.repository;

import com.techsutra.intruderalert.entity.UserAccount;
import com.techsutra.intruderalert.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findByToken(String token);

    void deleteByUser(UserAccount user);
}
