package com.migration.repository;

import com.migration.entity.CandidateMigration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.migration.entity.CandidateMigration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateMigrationRepository extends JpaRepository<CandidateMigration, Long> {

    List<CandidateMigration> findByStatus(String status);

    Optional<CandidateMigration> findByZohoCandidateId(String zohoCandidateId);

    @Query("SELECT c FROM CandidateMigration c WHERE c.status = :status AND (c.taggedInZoho IS NULL OR c.taggedInZoho = false)")
    List<CandidateMigration> findSuccessWithoutTag(@Param("status") String status);
}
