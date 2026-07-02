package com.migration.repository;

import com.migration.entity.MigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MigrationLogRepository extends JpaRepository<MigrationLog, Long> {

    List<MigrationLog> findByCandidateMigrationId(Long candidateMigrationId);

    List<MigrationLog> findByStatus(String status);

    @org.springframework.data.jpa.repository.Query("SELECT l FROM MigrationLog l WHERE l.status IN ('AVISO', 'ERRO') ORDER BY l.createdAt DESC")
    List<MigrationLog> findWarnings();
}
