package io.github.bodote.woodle.config;

import io.github.bodote.woodle.adapter.out.persistence.InMemoryPollRepository;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.application.service.CreatePollService;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.service.ReadPollService;
import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.service.SubmitVoteService;
import io.github.bodote.woodle.application.port.in.AdminPollOptionsUseCase;
import io.github.bodote.woodle.application.service.AdminPollOptionsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    @Bean
    @ConditionalOnMissingBean(PollRepository.class)
    @ConditionalOnProperty(name = "woodle.s3.enabled", havingValue = "false", matchIfMissing = true)
    public PollRepository pollRepository() {
        return new InMemoryPollRepository();
    }

    @Bean
    public CreatePollUseCase createPollUseCase(PollRepository pollRepository) {
        return new CreatePollService(pollRepository);
    }

    @Bean
    public ReadPollUseCase readPollUseCase(PollRepository pollRepository) {
        return new ReadPollService(pollRepository);
    }

    @Bean
    public SubmitVoteUseCase submitVoteUseCase(PollRepository pollRepository) {
        return new SubmitVoteService(pollRepository);
    }

    @Bean
    public AdminPollOptionsUseCase adminPollOptionsUseCase(PollRepository pollRepository) {
        return new AdminPollOptionsService(pollRepository);
    }
}
