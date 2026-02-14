package io.github.bodote.woodle.config;

import io.github.bodote.woodle.adapter.out.persistence.InMemoryPollRepository;
import io.github.bodote.woodle.adapter.out.persistence.S3PollRepository;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.application.service.CreatePollService;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.service.ReadPollService;
import io.github.bodote.woodle.application.port.in.SubmitVoteUseCase;
import io.github.bodote.woodle.application.service.SubmitVoteService;
import io.github.bodote.woodle.application.port.in.AdminPollOptionsUseCase;
import io.github.bodote.woodle.application.service.AdminPollOptionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class ApplicationConfig {

    @Bean
    @ConditionalOnMissingBean(PollRepository.class)
    public PollRepository pollRepository(
            @Value("${woodle.s3.enabled:false}") boolean s3Enabled,
            @Value("${woodle.s3.bucket:woodle}") String bucketName,
            ObjectProvider<S3Client> s3ClientProvider,
            ObjectMapper objectMapper
    ) {
        if (s3Enabled) {
            S3Client s3Client = s3ClientProvider.getIfAvailable();
            if (s3Client == null) {
                throw new IllegalStateException("S3 is enabled but no S3 client bean is available");
            }
            return new S3PollRepository(s3Client, objectMapper, bucketName);
        }
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
