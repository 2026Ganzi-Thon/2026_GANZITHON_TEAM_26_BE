package com.hanol.infra.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** OpenAI 호환 chat completions API (Upstage) 클라이언트. */
@Component
public class AiChatClient {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_ERROR_BODY_LENGTH = 300;

  private final AiProperties properties;
  private final JsonMapper jsonMapper;
  private final RestClient restClient;

  public AiChatClient(AiProperties properties, JsonMapper jsonMapper) {
    this.properties = properties;
    this.jsonMapper = jsonMapper;
    JdkClientHttpRequestFactory requestFactory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    requestFactory.setReadTimeout(READ_TIMEOUT);
    this.restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  /** 응답 content(schema 를 따르는 JSON 문자열)를 돌려준다. 실패하면 AiClientException. */
  public String complete(
      String systemPrompt, String userContent, String schemaName, Map<String, Object> schema) {
    Map<String, Object> body =
        Map.of(
            "model",
            properties.model(),
            "messages",
            List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)),
            "response_format",
            Map.of(
                "type",
                "json_schema",
                "json_schema",
                Map.of("name", schemaName, "strict", true, "schema", schema)));

    String responseBody;
    try {
      responseBody =
          restClient
              .post()
              .uri(stripTrailingSlash(properties.baseUrl()) + "/chat/completions")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(jsonMapper.writeValueAsString(body))
              .retrieve()
              .body(String.class);
    } catch (RestClientResponseException exception) {
      throw new AiClientException(
          "AI API error: status="
              + exception.getStatusCode().value()
              + ", body="
              + truncate(exception.getResponseBodyAsString()),
          exception);
    } catch (RestClientException | IllegalArgumentException exception) {
      throw new AiClientException("AI API call failed: " + exception.getMessage(), exception);
    }

    if (responseBody == null) {
      throw new AiClientException("AI API response is empty");
    }
    JsonNode content;
    try {
      content =
          jsonMapper.readTree(responseBody).path("choices").path(0).path("message").path("content");
    } catch (JacksonException exception) {
      throw new AiClientException("AI API response is not valid JSON", exception);
    }
    if (!content.isString() || content.asString().isBlank()) {
      throw new AiClientException("AI API response has no content");
    }
    return content.asString();
  }

  private static String stripTrailingSlash(String url) {
    if (url == null) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  private static String truncate(String text) {
    return text.length() <= MAX_ERROR_BODY_LENGTH ? text : text.substring(0, MAX_ERROR_BODY_LENGTH);
  }
}
