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

        // Verifica se a URL existe
        // Se estiver vazia ou null, lança erro
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("accountsUrl não pode ser vazio");
        }

        // Remove "/" no final da URL
        // Exemplo:
        // https://site.com/
        // vira:
        // https://site.com
        //
        // Isso evita URL com "//"
        url = url.replaceAll("/+$", "");

        // Monta o corpo da requisição HTTP
        // Esse formato é usado em requests "x-www-form-urlencoded"
        //
        // Exemplo final:
        // refresh_token=abc123
        // &client_id=meuClient
        // &client_secret=senha
        // &grant_type=refresh_token
        //
        // URLEncoder.encode serve para escapar caracteres especiais
        // como espaços, +, &, etc
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
        //
        // BodyHandlers.ofString():
        // transforma a resposta em String
        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        log.info("Token response status: {}", response.statusCode());

        if (response.statusCode() != 200) {

            // Se não veio 200,
            // lança erro mostrando status e body
            throw new RuntimeException(
                    "Erro ao gerar token. Status: "
                            + response.statusCode()
                            + " Body: "
                            + response.body()
            );
        }

        // Converte o JSON da resposta em objeto JsonNode
        //
        // Exemplo de resposta:
        // {
        //   "access_token": "abc123"
        // }
        JsonNode json = mapper.readTree(response.body());

        // Procura o campo "access_token" dentro do JSON
        JsonNode accessTokenNode = json.get("access_token");

        // Verifica se o campo existe
        if (accessTokenNode == null || accessTokenNode.isNull()) {

            // Se não existir, lança erro
            throw new RuntimeException(
                    "access_token não encontrado na resposta: "
                            + response.body()
            );
        }

        // Retorna o token como String
        return accessTokenNode.asText();
    }
}
