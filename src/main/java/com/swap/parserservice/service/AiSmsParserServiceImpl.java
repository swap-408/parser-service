package com.swap.parserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swap.parserservice.dto.ParsedExpenseDto;
import com.swap.parserservice.dto.SmsEventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class AiSmsParserServiceImpl implements AiSmsParserService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    public AiSmsParserServiceImpl(
            RestClient.Builder restClientBuilder,
            @Value("${mistral.api.key}") String apiKey,
            @Value("${mistral.model}") String model
    ) {
        this.model = model;
        this.restClient = restClientBuilder
                .baseUrl("https://api.mistral.ai/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ParsedExpenseDto parse(SmsEventDto event) {
        try {
            String prompt = """
                    Decide whether this SMS is a financial transaction message.

                    Return only valid JSON in this exact shape:
                    {
                      "isTransaction": true/false,
                      "confidence": 0.0,
                      "reason": "short explanation or null",
                      "amount": 0.0 or null,
                      "currency": "INR or other currency or null",
                      "merchant": "string or null",
                      "category": "string or null",
                      "transactionType": "DEBIT/CREDIT/TRANSFER or null",
                      "bank": "string or null",
                      "last4": "string or null",
                      "transactionDateTime": "string or null"
                    }

                    Rules:
                    - If this is not a transaction SMS, set "isTransaction" to false and keep the rest null where possible.
                    - If it is a transaction SMS, set "isTransaction" to true and extract all useful fields.
                    - confidence must be between 0 and 1.
                    - Do not wrap the answer in markdown.
                    - Do not add any explanation outside JSON.
                    - Preserve the SMS meaning accurately.

                    SMS:
                    %s
                    """.formatted(event.getMessage());

            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", "You are a strict JSON-only parser for bank SMS messages."
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", prompt
                            )
                    ),
                    "response_format", Map.of("type", "json_object"),
                    "temperature", 0.2
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            String content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText();

            String json = extractJson(content);
            JsonNode parsed = objectMapper.readTree(json);

            boolean isTransaction = parsed.path("isTransaction").asBoolean(false);
            if (!isTransaction) {
                return null;
            }

            ParsedExpenseDto dto = new ParsedExpenseDto();
            dto.setUserEmail(event.getUserEmail());
            dto.setAmount(parsed.path("amount").isMissingNode() || parsed.path("amount").isNull()
                    ? null
                    : new BigDecimal(parsed.path("amount").asText()));
            dto.setCurrency(textOrNull(parsed, "currency"));
            dto.setMerchant(textOrNull(parsed, "merchant"));
            dto.setCategory(textOrNull(parsed, "category"));
            dto.setTransactionType(textOrNull(parsed, "transactionType"));
            dto.setBank(textOrNull(parsed, "bank"));
            dto.setLast4(textOrNull(parsed, "last4"));
            dto.setTransactionDateTime(textOrNull(parsed, "transactionDateTime"));
            dto.setConfidence(parsed.path("confidence").isMissingNode() || parsed.path("confidence").isNull()
                    ? null
                    : parsed.path("confidence").asDouble());
            dto.setRawMessage(event.getMessage());

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SMS with Mistral", e);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String extractJson(String text) {
        String cleaned = text.trim();

        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }

        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');

        if (start >= 0 && end >= start) {
            cleaned = cleaned.substring(start, end + 1);
        }

        return cleaned.trim();
    }
}