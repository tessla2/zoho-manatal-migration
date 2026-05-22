package com.migration.batch.migration;

import com.migration.entity.CandidateMigration;
import com.migration.model.ManatalCandidate;
import lombok.Data;

import java.util.List;

@Data
public class CandidateMigrationPackage {

    private CandidateMigration candidateMigration;
    private ManatalCandidate transformed;
    private String zohoCandidateId;
    private Long manatalCandidateId;
    private String applicationId;
    private List<Long> storedAttachmentIds;
    private String noteInfo;
    private List<String> zohoNotes;
    private String linkedinUrl;
    private String errorMessage;
}
