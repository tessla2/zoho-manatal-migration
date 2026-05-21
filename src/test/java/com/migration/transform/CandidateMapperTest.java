package com.migration.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.model.ManatalCandidate;
import com.migration.transform.utils.ParseUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateMapperTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private CandidateMapper candidateMapper;
    private ParseUtils utils;

    @BeforeEach
    void setUp() {
        candidateMapper = new CandidateMapper();
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
    @DisplayName("LinkedIn integrado no mapper completo")
    void linkedinIntegrado() throws Exception {
        String json = """
                {"data": [{"LinkedIn__s": "linkedin.com/in/joao"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals("https://linkedin.com/in/joao", result.getLinkedin());
    }

    @Test
    @DisplayName("Skills integrado no mapper completo filtra validas")
    void skillsIntegrado() throws Exception {
        String json = """
                {"data": [{"Stacks_LinkedIn": "Python\\neMarketing\\nJava\\nCanva"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(Arrays.asList("eMarketing", "Canva"), result.getSkills());
    }

    @Test
    @DisplayName("Owner extraido como integer id de Candidate_Owner")
    void ownerExtraido() throws Exception {
        String json = """
                {"data": [{"Candidate_Owner": {"id": 123, "name": "Maria"}}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertEquals(Integer.valueOf(123), result.getOwner());
    }

    @Test
    @DisplayName("Custom fields populado com linkedin e skills")
    void customFieldsPopulado() throws Exception {
        String json = """
                {"data": [{"Full_Name": "Joao", "LinkedIn__s": "linkedin.com/in/joao", "Stacks_LinkedIn": "eMarketing"}]}
                """;
        JsonNode root = mapper.readTree(json).path("data").get(0);
        ManatalCandidate result = candidateMapper.toManatal(root);
        assertNotNull(result.getCustom_fields());
        assertEquals("https://linkedin.com/in/joao", result.getCustom_fields().get("linkedin"));
        assertEquals(List.of("eMarketing"), result.getCustom_fields().get("skills"));
    }
}
