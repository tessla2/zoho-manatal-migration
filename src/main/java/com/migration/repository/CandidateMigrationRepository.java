package com.migration.repository;

import com.migration.entity.CandidateMigration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateMigrationRepository extends JpaRepository<CandidateMigration, Long> {

    List<CandidateMigration> findByStatus(String status);

    Optional<CandidateMigration> findByZohoCandidateId(String zohoCandidateId);
}
