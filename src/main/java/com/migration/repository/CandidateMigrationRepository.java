package com.migration.repository;

import com.migration.entity.CandidateMigration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateMigrationRepository extends JpaRepository<CandidateMigration, Long> {

    List<CandidateMigration> findByStatus(String status);

    Optional<CandidateMigration> findByZohoCandidateId(String zohoCandidateId);

    @Query("SELECT c FROM CandidateMigration c WHERE c.status = :status AND (c.taggedInZoho IS NULL OR c.taggedInZoho = false)")
    List<CandidateMigration> findSuccessWithoutTag(@Param("status") String status);

    @Query(value = """
            SELECT * FROM candidate_migration c
            WHERE (:status IS NULL OR c.status = :status)
            AND (:search IS NULL OR c.zoho_candidate_id LIKE %:search% OR c.manatal_candidate_id LIKE %:search%)
            ORDER BY c.id
            """,
            countQuery = """
            SELECT count(*) FROM candidate_migration c
            WHERE (:status IS NULL OR c.status = :status)
            AND (:search IS NULL OR c.zoho_candidate_id LIKE %:search% OR c.manatal_candidate_id LIKE %:search%)
            """,
            nativeQuery = true)
    Page<CandidateMigration> findFiltered(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    List<CandidateMigration> findTop50ByOrderByIdDesc();

    @Modifying
    @Transactional
    @Query("UPDATE CandidateMigration c SET c.status = 'PENDENTE', c.errorMessage = NULL, c.updatedAt = CURRENT_TIMESTAMP WHERE c.status = 'ERRO'")
    int resetErrosParaPendente();

    @Query(value = """
            SELECT CAST(created_at AS DATE) AS day,
                   COUNT(*) AS total,
                   COUNT(CASE WHEN status = 'SUCESSO' THEN 1 END) AS sucesso,
                   COUNT(CASE WHEN status = 'ERRO' THEN 1 END) AS erro,
                   COUNT(CASE WHEN status = 'PENDENTE' THEN 1 END) AS pendente
            FROM candidate_migration
            GROUP BY CAST(created_at AS DATE)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyStats();
}
