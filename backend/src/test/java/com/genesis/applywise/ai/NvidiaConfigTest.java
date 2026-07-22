package com.genesis.applywise.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NvidiaConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NvidiaConfig.class)
            .withBean(FakeAiAnalysisClient.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withPropertyValues(
                    "ai.nvidia.base-url=https://integrate.api.nvidia.com/v1",
                    "ai.nvidia.model=nvidia/nemotron-3-super-120b-a12b",
                    "ai.nvidia.timeout-seconds=120",
                    "ai.nvidia.max-tokens=4096",
                    "ai.nvidia.max-input-characters=50000"
            );

    @Test
    void fakeIsTheDefaultProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiAnalysisClient.class))
                    .isInstanceOf(FakeAiAnalysisClient.class);
            assertThat(context).doesNotHaveBean(NvidiaAnalysisClient.class);
        });
    }

    @Test
    void selectsNvidiaOnlyThroughConfiguration() {
        contextRunner
                .withPropertyValues(
                        "ai.provider=nvidia",
                        "ai.nvidia.api-key=test-placeholder-key"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AiAnalysisClient.class))
                            .isInstanceOf(NvidiaAnalysisClient.class);
                    assertThat(context).hasSingleBean(FakeAiAnalysisClient.class);
                });
    }

    @Test
    void refusesToStartNvidiaWithoutApiKey() {
        contextRunner
                .withPropertyValues("ai.provider=nvidia")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("NVIDIA_API_KEY is required when AI_PROVIDER=nvidia");
                });
    }

    @Test
    void rejectsUnsupportedProviderInsteadOfFallingBackToFake() {
        contextRunner
                .withPropertyValues("ai.provider=unsupported")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("AI_PROVIDER must be either fake or nvidia");
                });
    }
}
