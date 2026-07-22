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
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NvidiaProperties properties;
    private final AnalysisPromptBuilder promptBuilder;

    public NvidiaAnalysisClient(
            RestClient restClient,
            ObjectMapper objectMapper,
            NvidiaProperties properties,
            AnalysisPromptBuilder promptBuilder
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
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
                throw invalidResponse();
            }
            return response;
        } catch (NvidiaProviderException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpError(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw temporarilyUnavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    private NvidiaProviderException mapHttpError(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new NvidiaProviderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "NVIDIA authentication failed. Check the configured NVIDIA_API_KEY."
            );
        }
        if (statusCode == 429) {
            return new NvidiaProviderException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "NVIDIA rate limit reached. Try again later."
            );
        }
        if (statusCode >= 500) {
            return temporarilyUnavailable();
        }
        return invalidResponse();
    }

    private String responseContent(NvidiaChatResponse response) {
        if (response.choices() == null || response.choices().isEmpty()) {
            throw invalidResponse();
        }
        NvidiaChatResponse.Choice choice = response.choices().getFirst();
        if (choice == null || choice.message() == null
                || choice.message().content() == null || choice.message().content().isBlank()) {
            throw invalidResponse();
        }
        return choice.message().content();
    }

    private AnalysisResult parseAndValidate(String content, String resumeContent) {
        try {
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(content);
            validateResultNode(root, resumeContent);
            return objectMapper.treeToValue(root, AnalysisResult.class);
        } catch (NvidiaProviderException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException exception) {
            throw invalidResponse();
        }
    }

    private void validateResultNode(JsonNode root, String resumeContent) {
        requireObjectWithExactFields(root, RESULT_FIELDS);

        JsonNode score = root.get("matchScore");
        if (!score.isIntegralNumber() || score.intValue() < 0 || score.intValue() > 100) {
            throw invalidResponse();
        }
        requireNonBlankText(root.get("summary"));
        validateSkills(root.get("skills"), resumeContent);
        validateStringArray(root.get("strengths"));
        validateStringArray(root.get("gaps"));
        validateStringArray(root.get("recommendedActions"));
    }

    private void validateSkills(JsonNode skills, String resumeContent) {
        if (skills == null || !skills.isArray()) {
            throw invalidResponse();
        }
        for (JsonNode skill : skills) {
            requireObjectWithExactFields(skill, SKILL_FIELDS);
            requireNonBlankText(skill.get("name"));
            requireNonBlankText(skill.get("status"));
            requireNonBlankText(skill.get("explanation"));

            MatchStatus status;
            try {
                status = MatchStatus.valueOf(skill.get("status").textValue());
            } catch (IllegalArgumentException exception) {
                throw invalidResponse();
            }

            JsonNode evidence = skill.get("resumeEvidence");
            if (evidence == null || (!evidence.isNull() && !evidence.isTextual())) {
                throw invalidResponse();
            }
            if ((status == MatchStatus.MATCHED || status == MatchStatus.PARTIAL)
                    && (evidence.isNull() || evidence.textValue().isBlank())) {
                throw invalidResponse();
            }
            if (evidence.isTextual() && !evidence.textValue().isBlank()
                    && !normalized(resumeContent).contains(normalized(evidence.textValue()))) {
                throw invalidResponse();
            }
        }
    }

    private void validateStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw invalidResponse();
        }
        node.forEach(this::requireNonBlankText);
    }

    private void requireObjectWithExactFields(JsonNode node, Set<String> expectedFields) {
        if (node == null || !node.isObject()) {
            throw invalidResponse();
        }
        Set<String> actualFields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actualFields::add);
        if (!actualFields.equals(expectedFields)) {
            throw invalidResponse();
        }
    }

    private void requireNonBlankText(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalidResponse();
        }
    }

    private String normalized(String value) {
        return WHITESPACE.matcher(value.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private NvidiaProviderException temporarilyUnavailable() {
        return new NvidiaProviderException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NVIDIA analysis service is temporarily unavailable."
        );
    }

    private NvidiaProviderException invalidResponse() {
        return new NvidiaProviderException(
                HttpStatus.BAD_GATEWAY,
                "NVIDIA returned an invalid analysis response."
        );
    }
}
