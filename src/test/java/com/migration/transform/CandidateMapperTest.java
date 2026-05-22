package com.migration.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.model.ManatalCandidate;
import com.migration.transform.utils.ParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private CandidateMapper candidateMapper;
    private ParseUtils utils;

    private static final Integer MANATAL_USER_ID = 1193857;

    @BeforeEach
    void setUp() {
        utils = new ParseUtils();
        candidateMapper = new CandidateMapper(utils);
    }

    // ────────────── LinkedIn ──────────────

    @Test
    @DisplayName("LinkedIn com https mantem igual")
    void linkedinComHttps() {
        String result = utils.normalizeLinkedin("https://linkedin.com/in/fulano");
        assertEquals("https://linkedin.com/in/fulano", result);
    }

    @Test
    @DisplayName("LinkedIn sem protocolo prefixa https://")
    void linkedinSemProtocolo() {
        String result = utils.normalizeLinkedin("linkedin.com/in/fulano");
        assertEquals("https://linkedin.com/in/fulano", result);
    }

    @Test
    @DisplayName("LinkedIn apenas username prefixa https://")
    void linkedinApenasUsuario() {
        String result = utils.normalizeLinkedin("fulano");
        assertEquals("https://fulano", result);
    }

    @Test
    @DisplayName("LinkedIn nulo retorna null")
    void linkedinNulo() {
        assertNull(utils.normalizeLinkedin(null));
    }

    @Test
    @DisplayName("LinkedIn vazio retorna null")
    void linkedinVazio() {
        assertNull(utils.normalizeLinkedin(""));
        assertNull(utils.normalizeLinkedin("   "));
    }

    @Test
    @DisplayName("LinkedIn com http mantem (caso exista)")
    void linkedinComHttp() {
        String result = utils.normalizeLinkedin("http://linkedin.com/in/fulano");
        assertEquals("http://linkedin.com/in/fulano", result);
    }

    @Test
    @DisplayName("LinkedIn com espacos nas bordas e sem protocolo")
    void linkedinComEspacos() {
        String result = utils.normalizeLinkedin("  linkedin.com/in/fulano  ");
        assertEquals("https://linkedin.com/in/fulano", result);
    }


    @Test
    @DisplayName("Full name mapeado de Full_Name")
    void fullNameMapeado() throws Exception {
        String json = """
                {"data": [{"Full_Name": "João Silva"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("João Silva", result.getFull_name());
    }

    @Test
    @DisplayName("Email mapeado de Email")
    void emailMapeado() throws Exception {
        String json = """
                {"data": [{"Email": "joao@email.com"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("joao@email.com", result.getEmail());
    }

    @Test
    @DisplayName("Phone mapeado de Phone")
    void phoneMapeado() throws Exception {
        String json = """
                {"data": [{"Phone": "11999999999"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("11999999999", result.getPhone_number());
    }

    @Test
    @DisplayName("extractLinkedinUrl usa $social_profiles primeiro")
    void extractLinkedinUrlSocialProfiles() throws Exception {
        String json = """
                {"data": [{"$social_profiles": "https://linkedin.com/in/joao||||||||||||"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        assertEquals("https://linkedin.com/in/joao", candidateMapper.extractLinkedinUrl(root));
    }

    @Test
    @DisplayName("extractLinkedinUrl tenta LinkedIn__s como fallback")
    void extractLinkedinUrl() throws Exception {
        String json = """
                {"data": [{"LinkedIn__s": "linkedin.com/in/joao"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        assertEquals("https://linkedin.com/in/joao", candidateMapper.extractLinkedinUrl(root));
    }

    @Test
    @DisplayName("extractLinkedinUrl com LinkedIn (sem __s)")
    void extractLinkedinUrlSemSuffix() throws Exception {
        String json = """
                {"data": [{"LinkedIn": "linkedin.com/in/maria"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        assertEquals("https://linkedin.com/in/maria", candidateMapper.extractLinkedinUrl(root));
    }

    @Test
    @DisplayName("extractLinkedinUrl retorna null quando sem dados")
    void extractLinkedinUrlNull() throws Exception {
        String json = """
                {"data": [{"Full_Name": "Joao"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        assertNull(candidateMapper.extractLinkedinUrl(root));
    }

    @Test
    @DisplayName("candidate_location usa Candidate_City quando City é -None-")
    void candidateLocationFallbackCity() throws Exception {
        String json = """
                {"data": [{"City": "-None-", "Candidate_City": "Oliveira de Azemeis", "Country": "Portugal"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Oliveira de Azemeis, Portugal", result.getCandidate_location());
    }

    @Test
    @DisplayName("candidate_location combina City + Country")
    void candidateLocationCityCountry() throws Exception {
        String json = """
                {"data": [{"City": "Lisbon", "Country": "Portugal"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Lisbon, Portugal", result.getCandidate_location());
    }

    @Test
    @DisplayName("candidate_location usa City quando so existe City")
    void candidateLocationOnlyCity() throws Exception {
        String json = """
                {"data": [{"City": "Lisbon"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Lisbon", result.getCandidate_location());
    }

    @Test
    @DisplayName("candidate_location usa Country quando so existe Country")
    void candidateLocationOnlyCountry() throws Exception {
        String json = """
                {"data": [{"Country": "Portugal"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Portugal", result.getCandidate_location());
    }

    @Test
    @DisplayName("candidate_location null quando sem City nem Country")
    void candidateLocationNull() throws Exception {
        String json = """
                {"data": [{"Full_Name": "Joao"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertNull(result.getCandidate_location());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Skills parseadas do Stacks_LinkedIn (newline) dentro de custom_fields")
    void skillsNewline() throws Exception {
        String json = """
                {"data": [{"Stacks_LinkedIn": "Python\\neMarketing\\nJava\\nCanva"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(Arrays.asList("Python", "eMarketing", "Java", "Canva"), result.getCustom_fields().get("skills"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Skills parseadas do Stacks_LinkedIn (comma) dentro de custom_fields")
    void skillsComma() throws Exception {
        String json = """
                {"data": [{"Stacks_LinkedIn": "LinkedIn, Docker, Git, Spring Boot"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(Arrays.asList("LinkedIn", "Docker", "Git", "Spring Boot"), result.getCustom_fields().get("skills"));
    }

    @Test
    @DisplayName("Owner fixo 1193857")
    void ownerFixo() throws Exception {
        String json = """
                {"data": [{"Full_Name": "Joao"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(MANATAL_USER_ID, result.getOwner());
    }

    @Test
    @DisplayName("Creator fixo 1193857 (nao mais extraido do Zoho)")
    void creatorFixo() throws Exception {
        String json = """
                {"data": [{"Candidate_Owner": {"id": 123, "name": "Maria"}}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(MANATAL_USER_ID, result.getCreator());
    }

    @Test
    @DisplayName("intField parseia string numerica em custom_fields")
    void intFieldString() throws Exception {
        String json = """
                {"data": [{"Availability_Days": "30", "Number_of_Dependants": "2"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(4, result.getCustom_fields().get("availabilityweeks"));
        assertEquals(2, result.getCustom_fields().get("numberofdependants"));
    }

    @Test
    @DisplayName("Consent mapeado Given -> true")
    void consentGiven() throws Exception {
        String json = """
                {"data": [{"Consent_to_RGPD": "Given"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(true, result.getConsent());
    }

    @Test
    @DisplayName("Consent mapeado null -> false/null")
    void consentNull() throws Exception {
        String json = """
                {"data": [{"Full_Name": "Joao"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertNull(result.getConsent());
    }

    @Test
    @DisplayName("ccurrency e ecurrency mapeados de Currency")
    void currencyMapeado() throws Exception {
        String json = """
                {"data": [{"Currency": "EUR"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("EUR", result.getCcurrency());
        assertEquals("EUR", result.getEcurrency());
    }

    @Test
    @DisplayName("yearofexperience mapeado de Experience_in_Years")
    void yearOfExperienceMapeado() throws Exception {
        String json = """
                {"data": [{"Experience_in_Years": 5}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(Integer.valueOf(5), result.getYearofexperience());
    }

    @Test
    @DisplayName("country nativo mapeado de Country")
    void countryNativeMapeado() throws Exception {
        String json = """
                {"data": [{"Country": "Portugal"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Portugal", result.getCountry());
    }

    @Test
    @DisplayName("city em custom_fields mapeado de City")
    void cityCustomField() throws Exception {
        String json = """
                {"data": [{"City": "Lisbon"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Lisbon", result.getCustom_fields().get("city"));
    }

    @Test
    @DisplayName("salary_notes em custom_fields mapeado de Salary_Notes")
    void salaryNotesCustomField() throws Exception {
        String json = """
                {"data": [{"Salary_Notes": "Negotiable"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("Negotiable", result.getCustom_fields().get("salary_notes"));
    }

    @Test
    @DisplayName("Custom fields com nomes corretos do Manatal")
    void customFieldsManatalNames() throws Exception {
        String json = """
                {"data": [
                    {"Full_Name": "Joao",
                     "Relocation": "Yes",
                     "WorkVisa": "Yes",
                     "Civil_Status": "Married",
                     "Availability_Days": 14,
                     "Number_of_Dependants": 1,
                     "Consent_to_RGPD": "Given",
                     "Additional_Information": "Teste"}
                ]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertNotNull(result.getCustom_fields());
        assertEquals(true, result.getCustom_fields().get("canrelocate"));
        assertEquals(true, result.getCustom_fields().get("workvisaeucitizenship"));
        assertEquals("Married", result.getCustom_fields().get("civilstatus"));
        assertEquals(2, result.getCustom_fields().get("availabilityweeks"));
        assertEquals(1, result.getCustom_fields().get("numberofdependants"));
        assertEquals("Teste", result.getCustom_fields().get("additional_info"));
    }
}
