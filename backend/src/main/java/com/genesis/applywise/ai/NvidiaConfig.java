package com.genesis.applywise.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NvidiaProperties.class)
public class NvidiaConfig {

    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("fake", "nvidia");

    private final String provider;

    public NvidiaConfig(@Value("${ai.provider:fake}") String provider) {
        this.provider = provider;
    }

    @PostConstruct
    void validateProvider() {
        if (!SUPPORTED_PROVIDERS.contains(provider)) {
            throw new IllegalStateException("AI_PROVIDER must be either fake or nvidia");
        }
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.provider", havingValue = "nvidia")
    AiAnalysisClient nvidiaAnalysisClient(NvidiaProperties properties, ObjectMapper objectMapper) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("NVIDIA_API_KEY is required when AI_PROVIDER=nvidia");
        }

        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(withoutTrailingSlash(properties.baseUrl().toString()))
                .requestFactory(requestFactory)
                .build();

        return new NvidiaAnalysisClient(
                restClient,
                objectMapper,
                properties,
                new AnalysisPromptBuilder(objectMapper, properties.maxInputCharacters()),
                new AnalysisResultValidator(new ResumeEvidenceValidator())
        );
    }

    private static String withoutTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
