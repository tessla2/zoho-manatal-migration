package com.migration.repository;

import com.migration.entity.StoredAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoredAttachmentRepository extends JpaRepository<StoredAttachment, Long> {

    List<StoredAttachment> findByCandidateId(String candidateId);

    List<StoredAttachment> findByZohoAttachmentId(String zohoAttachmentId);
}
