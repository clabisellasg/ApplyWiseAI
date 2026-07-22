package com.genesis.applywise.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NvidiaAnalysisClientTest {

    private static final String BASE_URL = "https://nvidia.example.test/v1";
    private static final String API_KEY = "test-placeholder-key";
    private static final String MODEL = "nvidia/nemotron-3-super-120b-a12b";
    private static final String RESUME = "Built Java services and maintained REST APIs.";
    private static final String JOB = "Java, REST APIs, and Docker are required.";

    private ObjectMapper objectMapper;
    private MockRestServiceServer server;
    private NvidiaAnalysisClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = client(builder.build(), 50000);
    }

    @Test
    void sendsGuidedJsonRequestAndMapsValidResponse() throws Exception {
        server.expect(once(), requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.temperature").value(1.0))
                .andExpect(jsonPath("$.top_p").value(0.95))
                .andExpect(jsonPath("$.max_tokens").value(4096))
                .andExpect(jsonPath("$.chat_template_kwargs.enable_thinking").value(false))
                .andExpect(jsonPath("$.nvext.guided_json.required[0]").value("matchScore"))
                .andExpect(jsonPath("$.nvext.guided_json.properties.skills.items.properties.status.enum[0]")
                        .value("MATCHED"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value(org.hamcrest.Matchers.containsString(RESUME)))
                .andRespond(withSuccess(providerResponse(validResult()), MediaType.APPLICATION_JSON));

        AnalysisResult result = client.analyze(RESUME, JOB);

        assertThat(result.matchScore()).isEqualTo(50);
        assertThat(result.skills()).extracting(SkillAssessment::status)
                .containsExactly(MatchStatus.MATCHED, MatchStatus.MISSING);
        assertThat(client.provider()).isEqualTo("nvidia");
        assertThat(client.model()).isEqualTo(MODEL);
        assertThat(client.promptVersion()).isEqualTo("nvidia-nemotron-v1");
        server.verify();
    }

    @Test
    void mapsInvalidCredentialsToSafeServiceError() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("provider details must not be exposed")
                        .contentType(MediaType.TEXT_PLAIN));

        assertProviderFailure(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NVIDIA authentication failed. Check the configured NVIDIA_API_KEY."
        );
    }

    @Test
    void mapsRateLimitingToTooManyRequests() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertProviderFailure(HttpStatus.TOO_MANY_REQUESTS, "NVIDIA rate limit reached. Try again later.");
    }

    @Test
    void mapsTemporaryProviderFailureToServiceUnavailable() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertProviderFailure(HttpStatus.SERVICE_UNAVAILABLE, "NVIDIA analysis service is temporarily unavailable.");
    }

    @Test
    void mapsTimeoutToServiceUnavailable() {
        RestClient timeoutRestClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory((uri, httpMethod) -> {
                    throw new SocketTimeoutException("simulated timeout");
                })
                .build();
        NvidiaAnalysisClient timeoutClient = client(timeoutRestClient, 50000);

        assertThatThrownBy(() -> timeoutClient.analyze(RESUME, JOB))
                .isInstanceOfSatisfying(NvidiaProviderException.class, exception -> {
                    assertThat(exception.getResponseStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception).hasMessage("NVIDIA analysis service is temporarily unavailable.");
                });
    }

    @Test
    void rejectsEmptyChoices() {
        respondWithOuterJson("{\"choices\":[]}");

        assertInvalidResponse();
    }

    @Test
    void rejectsMissingMessageContent() {
        respondWithOuterJson("{\"choices\":[{\"message\":{}}]}");

        assertInvalidResponse();
    }

    @Test
    void rejectsMalformedResultJson() throws Exception {
        respondWithResult("{not-json");

        assertInvalidResponse();
    }

    @Test
    void rejectsOutOfRangeMatchScore() throws Exception {
        ObjectNode result = validResult();
        result.put("matchScore", 101);
        respondWithResult(result.toString());

        assertInvalidResponse();
    }

    @Test
    void rejectsInvalidMatchStatus() throws Exception {
        ObjectNode result = validResult();
        ((ObjectNode) result.withArray("skills").get(0)).put("status", "EXCELLENT");
        respondWithResult(result.toString());

        assertInvalidResponse();
    }

    @Test
    void rejectsIncompleteResult() throws Exception {
        ObjectNode result = validResult();
        result.remove("recommendedActions");
        respondWithResult(result.toString());

        assertInvalidResponse();
    }

    @Test
    void rejectsResumeEvidenceThatIsNotGroundedInResume() throws Exception {
        ObjectNode result = validResult();
        ((ObjectNode) result.withArray("skills").get(0))
                .put("resumeEvidence", "Led a team of 40 engineers.");
        respondWithResult(result.toString());

        assertInvalidResponse();
    }

    @Test
    void rejectsMatchedSkillWithoutResumeEvidence() throws Exception {
        ObjectNode result = validResult();
        ((ObjectNode) result.withArray("skills").get(0)).putNull("resumeEvidence");
        respondWithResult(result.toString());

        assertInvalidResponse();
    }

    @Test
    void rejectsOversizedInputWithoutCallingProvider() {
        NvidiaAnalysisClient limitedClient = client(RestClient.create(BASE_URL), 10);

        assertThatThrownBy(() -> limitedClient.analyze("12345678901", JOB))
                .isInstanceOfSatisfying(NvidiaProviderException.class, exception -> {
                    assertThat(exception.getResponseStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception).hasMessage(
                            "Resume exceeds the configured NVIDIA input limit of 10 characters."
                    );
                });
    }

    private NvidiaAnalysisClient client(RestClient restClient, int maxInputCharacters) {
        NvidiaProperties properties = new NvidiaProperties(
                API_KEY,
                URI.create(BASE_URL),
                MODEL,
                120,
                4096,
                maxInputCharacters
        );
        return new NvidiaAnalysisClient(
                restClient,
                objectMapper,
                properties,
                new AnalysisPromptBuilder(objectMapper, maxInputCharacters)
        );
    }

    private void assertProviderFailure(HttpStatus status, String message) {
        assertThatThrownBy(() -> client.analyze(RESUME, JOB))
                .isInstanceOfSatisfying(NvidiaProviderException.class, exception -> {
                    assertThat(exception.getResponseStatus()).isEqualTo(status);
                    assertThat(exception).hasMessage(message);
                });
        server.verify();
    }

    private void assertInvalidResponse() {
        assertProviderFailure(HttpStatus.BAD_GATEWAY, "NVIDIA returned an invalid analysis response.");
    }

    private void respondWithResult(String result) throws Exception {
        respondWithOuterJson(providerResponse(result));
    }

    private void respondWithOuterJson(String body) {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private String providerResponse(ObjectNode result) throws Exception {
        return providerResponse(result.toString());
    }

    private String providerResponse(String result) throws Exception {
        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode message = response.putArray("choices").addObject().putObject("message");
        message.put("content", result);
        return objectMapper.writeValueAsString(response);
    }

    private ObjectNode validResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("matchScore", 50);
        result.put("summary", "The resume has one of two requested skills.");
        ArrayNode skills = result.putArray("skills");
        skills.addObject()
                .put("name", "Java")
                .put("status", "MATCHED")
                .put("resumeEvidence", "Built Java services")
                .put("explanation", "The resume contains direct Java evidence.");
        skills.addObject()
                .put("name", "Docker")
                .put("status", "MISSING")
                .putNull("resumeEvidence")
                .put("explanation", "Docker is requested but is not evidenced.");
        result.putArray("strengths").add("Direct Java experience.");
        result.putArray("gaps").add("No Docker evidence.");
        result.putArray("recommendedActions").add("Learn Docker before claiming experience.");
        return result;
    }
}
