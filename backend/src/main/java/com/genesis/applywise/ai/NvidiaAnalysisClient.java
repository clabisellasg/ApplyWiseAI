package com.genesis.applywise.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NvidiaAnalysisClient implements AiAnalysisClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String PROMPT_VERSION = "nvidia-nemotron-v1";
    private static final Set<String> RESULT_FIELDS = Set.of(
            "matchScore", "summary", "skills", "strengths", "gaps", "recommendedActions"
    );
    private static final Set<String> SKILL_FIELDS = Set.of(
            "name", "status", "resumeEvidence", "explanation"
    );
    private static final Pattern COMPLETE_JSON_CODE_FENCE = Pattern.compile(
            "\\A```(?:json)?\\s*\\R?([\\s\\S]*?)\\R?```\\s*\\z",
            Pattern.CASE_INSENSITIVE
    );
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NvidiaProperties properties;
    private final AnalysisPromptBuilder promptBuilder;
    private final AnalysisResultValidator resultValidator;

    public NvidiaAnalysisClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            NvidiaProperties properties,
            AnalysisPromptBuilder promptBuilder,
            AnalysisResultValidator resultValidator
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.resultValidator = resultValidator;
    }

    @Override
    public AnalysisResult analyze(String resumeContent, String jobDescription) {
        AnalysisPromptBuilder.AnalysisPrompt prompt = promptBuilder.build(resumeContent, jobDescription);
        NvidiaChatRequest request = new NvidiaChatRequest(
                properties.model(),
                List.of(
                        new NvidiaChatRequest.Message("system", prompt.systemPrompt()),
                        new NvidiaChatRequest.Message("user", prompt.userPrompt())
                ),
                false,
                1.0,
                0.95,
                properties.maxTokens(),
                new NvidiaChatRequest.ChatTemplateKwargs(false),
                new NvidiaChatRequest.NvidiaExtension(prompt.jsonSchema())
        );

        NvidiaChatResponse response = executeRequest(request);
        String content = responseContent(response);
        return parseAndValidate(content, resumeContent);
    }

    @Override
    public String provider() {
        return "nvidia";
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    private NvidiaChatResponse executeRequest(NvidiaChatRequest request) {
        try {
            NvidiaChatResponse response = restClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(NvidiaChatResponse.class);
            if (response == null) {
                throw invalidResponse(AnalysisValidationFailure.MALFORMED_PROVIDER_RESPONSE);
            }
            return response;
        } catch (NvidiaProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw temporarilyUnavailable();
        } catch (RestClientException exception) {
            throw invalidResponse(AnalysisValidationFailure.MALFORMED_PROVIDER_RESPONSE);
        }
    }

    private NvidiaProviderException mapHttpError(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new NvidiaProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    NvidiaProviderException.Reason.AUTHENTICATION,
                    "NVIDIA authentication failed. Verify NVIDIA_API_KEY and restart the backend."
            );
        }
        if (statusCode == 429) {
            return new NvidiaProviderException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    NvidiaProviderException.Reason.RATE_LIMIT,
                    "NVIDIA rate limit reached. Wait before trying again."
            );
        }
        if (statusCode >= 500) {
            return temporarilyUnavailable();
        }
        return invalidResponse(AnalysisValidationFailure.MALFORMED_PROVIDER_RESPONSE);
    }

    private String responseContent(NvidiaChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            throw invalidResponse(AnalysisValidationFailure.EMPTY_PROVIDER_CONTENT);
        }
        NvidiaChatResponse.Choice choice = response.choices().getFirst();
        if (choice == null || choice.message() == null) {
            throw invalidResponse(AnalysisValidationFailure.RESPONSE_SCHEMA_MISMATCH);
        }
        if (choice.message().refusal() != null && !choice.message().refusal().isBlank()) {
            throw invalidResponse(AnalysisValidationFailure.PROVIDER_REFUSAL);
        }

        String finishReason = choice.finishReason() == null
                ? ""
                : choice.finishReason().toLowerCase(Locale.ROOT);
        if (finishReason.equals("length") || finishReason.equals("max_tokens")) {
            throw invalidResponse(AnalysisValidationFailure.TRUNCATED_PROVIDER_RESPONSE);
        }
        if (finishReason.equals("content_filter")) {
            throw invalidResponse(AnalysisValidationFailure.PROVIDER_REFUSAL);
        }

        String content = choice.message().content();
        if (content == null || content.isBlank()) {
            throw invalidResponse(AnalysisValidationFailure.EMPTY_PROVIDER_CONTENT);
        }
        return unwrapCompleteJsonCodeFence(content);
    }

    private AnalysisResult parseAndValidate(String content, String resumeContent) {
        JsonNode root;
        try {
            root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(content);
        } catch (JsonProcessingException exception) {
            throw invalidResponse(AnalysisValidationFailure.JSON_SYNTAX_ERROR);
        }

        validateResultNode(root);
        AnalysisResult result;
        try {
            result = objectMapper.treeToValue(root, AnalysisResult.class);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            throw invalidResponse(AnalysisValidationFailure.RESPONSE_SCHEMA_MISMATCH);
        }

        try {
            resultValidator.validate(result, resumeContent);
        } catch (AnalysisResultValidationException exception) {
            throw invalidResponse(exception.getFailure());
        }
        return result;
    }

    private void validateResultNode(JsonNode root) {
        requireObject(root);

        JsonNode score = root.get("matchScore");
        if (score == null
                || !score.isIntegralNumber()
                || score.intValue() < 0
                || score.intValue() > 100) {
            throw invalidResponse(AnalysisValidationFailure.INVALID_SCORE);
        }
        requireNonBlankText(root.get("summary"), AnalysisValidationFailure.MISSING_REQUIRED_TEXT);
        validateSkills(root.get("skills"));
        validateStringArray(root.get("strengths"));
        validateStringArray(root.get("gaps"));
        validateStringArray(root.get("recommendedActions"));
        rejectUnexpectedFields(root, RESULT_FIELDS);
    }

    private void validateSkills(JsonNode skills) {
        if (skills == null || !skills.isArray()) {
            throw invalidResponse(AnalysisValidationFailure.MISSING_REQUIRED_COLLECTION);
        }
        for (JsonNode skill : skills) {
            if (skill == null || !skill.isObject()) {
                throw invalidResponse(AnalysisValidationFailure.RESPONSE_SCHEMA_MISMATCH);
            }
            requireNonBlankText(skill.get("name"), AnalysisValidationFailure.BLANK_SKILL_NAME);
            requireNonBlankText(skill.get("status"), AnalysisValidationFailure.UNSUPPORTED_STATUS);
            requireNonBlankText(
                    skill.get("explanation"),
                    AnalysisValidationFailure.BLANK_SKILL_EXPLANATION
            );

            try {
                MatchStatus.valueOf(skill.get("status").textValue());
            } catch (IllegalArgumentException exception) {
                throw invalidResponse(AnalysisValidationFailure.UNSUPPORTED_STATUS);
            }

            JsonNode evidence = skill.get("resumeEvidence");
            if (evidence == null || (!evidence.isNull() && !evidence.isTextual())) {
                throw invalidResponse(AnalysisValidationFailure.UNSUPPORTED_EVIDENCE);
            }
            rejectUnexpectedFields(skill, SKILL_FIELDS);
        }
    }

    private void validateStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw invalidResponse(AnalysisValidationFailure.MISSING_REQUIRED_COLLECTION);
        }
        node.forEach(item -> requireNonBlankText(
                item,
                AnalysisValidationFailure.BLANK_REQUIRED_COLLECTION_ITEM
        ));
    }

    private void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidResponse(AnalysisValidationFailure.RESPONSE_SCHEMA_MISMATCH);
        }
    }

    private void rejectUnexpectedFields(JsonNode node, Set<String> expectedFields) {
        Set<String> actualFields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(expectedFields)) {
            throw invalidResponse(AnalysisValidationFailure.RESPONSE_SCHEMA_MISMATCH);
        }
    }

    private String unwrapCompleteJsonCodeFence(String content) {
        String stripped = content.strip();
        Matcher matcher = COMPLETE_JSON_CODE_FENCE.matcher(stripped);
        if (matcher.matches()) {
            String unwrapped = matcher.group(1).strip();
            if (unwrapped.isEmpty()) {
                throw invalidResponse(AnalysisValidationFailure.EMPTY_PROVIDER_CONTENT);
            }
            return unwrapped;
        }
        if (stripped.startsWith("```") || stripped.endsWith("```")) {
            throw invalidResponse(AnalysisValidationFailure.MARKDOWN_WRAPPED_JSON);
        }
        return stripped;
    }

    private void requireNonBlankText(JsonNode node, AnalysisValidationFailure failure) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalidResponse(failure);
        }
    }

    private NvidiaProviderException temporarilyUnavailable() {
        return new NvidiaProviderException(
                HttpStatus.SERVICE_UNAVAILABLE,
                NvidiaProviderException.Reason.TEMPORARY_UNAVAILABLE,
                "NVIDIA is temporarily unavailable. Try again later."
        );
    }

    private NvidiaProviderException invalidResponse(AnalysisValidationFailure validationFailure) {
        return new NvidiaProviderException(
                HttpStatus.BAD_GATEWAY,
                NvidiaProviderException.Reason.INVALID_RESPONSE,
                "NVIDIA returned an invalid analysis. Try again; no result was saved.",
                validationFailure
        );
    }
}
