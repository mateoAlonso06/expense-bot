package com.expensebot.accounts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, UUID> {

    // Native on purpose: ON CONFLICT is a PostgreSQL feature that JPQL cannot express.
    // created_at / updated_at come from the column defaults.
    @Modifying
    @Query(value = "INSERT INTO user_profile (id, email) VALUES (:id, :email) ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("email") String email);
}
