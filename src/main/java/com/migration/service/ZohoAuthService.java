package com.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.migration.config.ZohoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZohoAuthService {

    private final ZohoProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateAccessToken() throws IOException, InterruptedException {

        // Pega a URL base configurada nas propriedades
        String url = properties.oauth().tokenUrl();

        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("accountsUrl não pode ser vazio");
        }

        // Remove "/" no final da URL
        url = url.replaceAll("/+$", "");

        // Monta o corpo da requisição HTTP
        String body =
                "refresh_token=" + URLEncoder.encode(
                        properties.oauth().refreshToken(),
                        StandardCharsets.UTF_8
                ) +
                        "&client_id=" + URLEncoder.encode(
                        properties.oauth().clientId(),
                        StandardCharsets.UTF_8
                ) +
                        "&client_secret=" + URLEncoder.encode(
                        properties.oauth().clientSecret(),
                        StandardCharsets.UTF_8
                ) +
                        "&grant_type=refresh_token";

        // Cria a requisição HTTP
        log.info("Gerando token em: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                )
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // Cria um cliente HTTP
        // Ele será responsável por enviar a request
        HttpClient client = HttpClient.newHttpClient();

        // Envia a requisição para o servidor
        // e espera a resposta
        // BodyHandlers.ofString():
        // transforma a resposta em String
        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        log.info("Token response status: {}", response.statusCode());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Erro ao gerar token. Status: "
                            + response.statusCode()
                            + " Body: "
                            + response.body()
            );
        }

        // Converte o JSON da resposta em objeto JsonNode
        JsonNode json = mapper.readTree(response.body()); //jsonNode é um objeto que representa o JSON da resposta em Tree Model.

        // Procura o campo "access_token" dentro do JSON
        JsonNode accessTokenNode = json.get("access_token");

        if (accessTokenNode == null || accessTokenNode.isNull()) {
            throw new RuntimeException(
                    "access_token não encontrado na resposta: "
                            + response.body()
            );
        }
        return accessTokenNode.asText();
    }
}
