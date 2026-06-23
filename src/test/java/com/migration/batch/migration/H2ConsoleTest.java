package com.migration.batch.migration;

import com.migration.entity.CandidateMigration;
import com.migration.repository.CandidateMigrationRepository;
import com.migration.service.FileStorageService;
import com.migration.service.ManatalClientService;
import com.migration.service.ZohoClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.defer-datasource-initialization=true",
    "spring.batch.jdbc.initialize-schema=always"
})
@ActiveProfiles("dev")
class H2ConsoleTest {

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job candidateMigrationJob;

    @Autowired
    private CandidateMigrationRepository repository;

    @LocalServerPort
    private int port;

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

    @Test
    @DisplayName("Popula H2 com dados mock e pausa para inspeção visual")
    void testPopulateAndInspectH2() throws Exception {
        CandidateMigration candidate = new CandidateMigration();
        candidate.setZohoCandidateId(ZOHO_ID);
        candidate.setStatus("PENDENTE");
        repository.save(candidate);

        when(zohoClientService.fetchCandidateById(ZOHO_ID)).thenReturn(ZOHO_JSON);
        when(zohoClientService.listApplicationsByCandidate(ZOHO_ID)).thenReturn(APPLICATIONS_JSON);
        when(zohoClientService.listCandidateAttachments(ZOHO_ID)).thenReturn(CANDIDATE_ATTACHMENTS_JSON);
        when(zohoClientService.listApplicationAttachments(APP_ID)).thenReturn(APP_ATTACHMENTS_JSON);
        when(zohoClientService.saveAttachment(any(), any(), any(), any(), any(), any())).thenReturn(STORED_ATT_ID);
        when(manatalClientService.createCandidate(any())).thenReturn("{\"id\":" + MANATAL_ID + "}");
        when(fileStorageService.getFileName(STORED_ATT_ID)).thenReturn("CV_Original.pdf");
        when(fileStorageService.getContentType(STORED_ATT_ID)).thenReturn("application/pdf");
        doNothing().when(zohoClientService).tagCandidateWithTag(any(), any());
        doNothing().when(zohoClientService).removeTagFromCandidate(any(), any());

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        jobLauncher.run(candidateMigrationJob, params);

        List<CandidateMigration> all = repository.findAll();
        System.out.println("=== DADOS NO BANCO ===");
        for (CandidateMigration cm : all) {
            System.out.printf("id=%d, zohoId=%s, appId=%s, manatalId=%s, status=%s, taggedInZoho=%s%n",
                    cm.getId(), cm.getZohoCandidateId(), cm.getApplicationId(),
                    cm.getManatalCandidateId(), cm.getStatus(), cm.getTaggedInZoho());
        }
        System.out.println("======================");
        System.out.println("H2 Console: http://localhost:" + port + "/h2-console");
        System.out.println("JDBC URL: jdbc:h2:mem:migration");
        System.out.println("User: sa | Password: (empty)");
        System.out.println("O teste vai dormir por 120 segundos para inspeção manual.");
        System.out.println("Use Ctrl+C para encerrar antes do fim.");
        Thread.sleep(120000);
    }
}
