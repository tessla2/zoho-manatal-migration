package com.migration.repository;

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

    @Query("SELECT c FROM CandidateMigration c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:search IS NULL OR c.zohoCandidateId LIKE %:search% OR c.manatalCandidateId LIKE %:search%)")
    org.springframework.data.domain.Page<CandidateMigration> findFiltered(
            @Param("status") String status,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable);
}
