package com.migration.batch.migration;

import com.migration.entity.CandidateMigration;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.service.FileStorageService;
import com.migration.service.ManatalClientService;
import com.migration.service.ZohoClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.jpa.defer-datasource-initialization=true",
    "spring.batch.jdbc.initialize-schema=always"
})
@ActiveProfiles("dev")
class CandidateMigrationJobTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job candidateMigrationJob;

    @Autowired
    private CandidateMigrationRepository repository;

    @MockitoBean
    private ZohoClientService zohoClientService;

    @MockitoBean
    private ManatalClientService manatalClientService;

    @MockitoBean
    private FileStorageService fileStorageService;

    private static final String ZOHO_ID = "76333000000000001";
    private static final String APP_ID = "76333000000009001";
    private static final String MANATAL_ID = "456";
    private static final Long STORED_ATT_ID = 100L;

    private static final String ZOHO_JSON = """
            {"data":[{
                "Full_Name": "João Silva",
                "Email": "joao@email.com",
                "Phone": "11999999999",
                "Country": "Brazil",
                "Experience_in_Years": 5,
                "Availability_Days": 30,
                "Number_of_Dependants": 0,
                "Consent_to_RGPD": "yes",
                "Currency": "EUR",
                "LinkedIn__s": "linkedin.com/in/joao",
                "Stacks_LinkedIn": "Python\\neMarketing\\nJava\\nCanva",
                "current_salary": "50000",
                "expected_salary": "60000",
                "additional_info": "Disponível imediatamente",
                "Candidate_Owner": {"id": 123, "name": "Maria"},
                "Candidate_Description_Summary": "Senior developer",
                "Salary_Notes": "Negotiable",
                "Created_By": {"name": "Admin"},
                "Created_Time": "2024-01-01T00:00:00+00:00"
            }]}
            """;

    private static final String APPLICATIONS_JSON = """
            {"data":[{"id": "%s"}]}
            """.formatted(APP_ID);

    private static final String CANDIDATE_ATTACHMENTS_JSON = """
            {"data":[{"id":"att001","File_Name":"CV_Original.pdf","File_Type":"pdf","download_url":"https://zoho.com/att001"}]}
            """;

    private static final String APP_ATTACHMENTS_JSON = """
            {"data":[{"id":"att002","File_Name":"CV_Template.docx","File_Type":"docx","download_url":"https://zoho.com/att002"}]}
            """;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        CandidateMigration candidate = new CandidateMigration();
        candidate.setZohoCandidateId(ZOHO_ID);
        candidate.setStatus("PENDENTE");
        repository.save(candidate);
    }

    private void mockSuccessFlow() {
        when(zohoClientService.fetchCandidateById(ZOHO_ID)).thenReturn(ZOHO_JSON);
        when(zohoClientService.listApplicationsByCandidate(ZOHO_ID)).thenReturn(APPLICATIONS_JSON);
        when(zohoClientService.listCandidateAttachments(ZOHO_ID)).thenReturn(CANDIDATE_ATTACHMENTS_JSON);
        when(zohoClientService.listApplicationAttachments(APP_ID)).thenReturn(APP_ATTACHMENTS_JSON);
        when(zohoClientService.saveAttachment(any(), any(), any(), any(), any())).thenReturn(STORED_ATT_ID);

        when(manatalClientService.createCandidate(any())).thenReturn("{\"id\":" + MANATAL_ID + "}");

        when(fileStorageService.getFileName(STORED_ATT_ID)).thenReturn("CV_Original.pdf");
        when(fileStorageService.getContentType(STORED_ATT_ID)).thenReturn("application/pdf");

        doNothing().when(zohoClientService).tagCandidate(ZOHO_ID);
    }

    @Test
    @DisplayName("Job completa migração com attachments, resume e tag Zoho")
    void testFullMigrationWithAttachments() throws Exception {
        mockSuccessFlow();

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(candidateMigrationJob, params);

        Optional<CandidateMigration> result = repository.findByZohoCandidateId(ZOHO_ID);
        assertTrue(result.isPresent());
        assertEquals("SUCESSO", result.get().getStatus());
        assertEquals(MANATAL_ID, result.get().getManatalCandidateId());
        assertEquals(APP_ID, result.get().getApplicationId());
        assertTrue(result.get().getTaggedInZoho());

        verify(zohoClientService).tagCandidate(ZOHO_ID);
    }

    @Test
    @DisplayName("Job registra erro quando Zoho falha")
    void testMigrationJobWithZohoError() throws Exception {
        when(zohoClientService.fetchCandidateById(ZOHO_ID))
                .thenThrow(new RuntimeException("Zoho API error"));

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(candidateMigrationJob, params);

        Optional<CandidateMigration> result = repository.findByZohoCandidateId(ZOHO_ID);
        assertTrue(result.isPresent());
        assertEquals("ERRO", result.get().getStatus());
        assertNull(result.get().getTaggedInZoho());
    }

    @Test
    @DisplayName("Job registra erro quando Manatal falha (tag step não executa)")
    void testMigrationJobWithManatalError() throws Exception {
        when(zohoClientService.fetchCandidateById(ZOHO_ID)).thenReturn(ZOHO_JSON);
        when(zohoClientService.listApplicationsByCandidate(ZOHO_ID)).thenReturn(APPLICATIONS_JSON);
        when(zohoClientService.listCandidateAttachments(ZOHO_ID)).thenReturn(CANDIDATE_ATTACHMENTS_JSON);
        when(zohoClientService.listApplicationAttachments(APP_ID)).thenReturn(APP_ATTACHMENTS_JSON);
        when(zohoClientService.saveAttachment(any(), any(), any(), any(), any())).thenReturn(STORED_ATT_ID);

        when(manatalClientService.createCandidate(any()))
                .thenThrow(new RuntimeException("Manatal API error"));

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(candidateMigrationJob, params);

        Optional<CandidateMigration> result = repository.findByZohoCandidateId(ZOHO_ID);
        assertTrue(result.isPresent());
        assertEquals("ERRO", result.get().getStatus());
        assertNull(result.get().getTaggedInZoho());
    }
}
