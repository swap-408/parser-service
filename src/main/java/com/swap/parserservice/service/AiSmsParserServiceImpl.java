package com.swap.parserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swap.parserservice.dto.ParsedExpenseDto;
import com.swap.parserservice.dto.SmsEventDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class AiSmsParserServiceImpl implements AiSmsParserService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;
    private final String apiKey;

    public AiSmsParserServiceImpl(
            RestClient.Builder restClientBuilder,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = restClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ParsedExpenseDto parse(SmsEventDto event) {
        try {
            String prompt = """
                    Parse this Indian bank SMS into JSON.

                    Return only raw JSON.
                    Do not wrap it in markdown.
                    Do not add ```json fences.
                    Do not add any explanation.

                    Return only these fields:
                    userEmail, amount, currency, merchant, category,
                    transactionType, bank, last4, transactionDateTime, confidence, rawMessage

                    Rules:
                    - If a field is missing, return null
                    - confidence should be between 0 and 1
                    - rawMessage must be the exact SMS text
                    - transactionType should be DEBIT, CREDIT, or TRANSFER if possible

                    SMS:
                    %s
                    """.formatted(event.getMessage());

            Map<String, Object> body = Map.of(
                    "contents", new Object[]{
                            Map.of(
                                    "role", "user",
                                    "parts", new Object[]{
                                            Map.of("text", prompt)
                                    }
                            )
                    },
                    "generationConfig", Map.of(
                            "temperature", 0.2
                    )
            );

            String response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            String text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

            String json = extractJson(text);
            JsonNode parsed = objectMapper.readTree(json);

            ParsedExpenseDto dto = new ParsedExpenseDto();
            dto.setUserEmail(event.getUserEmail());
            dto.setAmount(parsed.path("amount").isNull() ? null : new BigDecimal(parsed.path("amount").asText()));
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
            throw new RuntimeException("Failed to parse SMS with Gemini", e);
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