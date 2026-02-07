package io.github.bodote.woodle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bodote.woodle.adapter.out.persistence.S3PollRepository;
import io.github.bodote.woodle.application.port.out.PollRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(name = "woodle.s3.enabled", havingValue = "true")
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${woodle.s3.endpoint:}") String endpoint,
            @Value("${woodle.s3.region:eu-central-1}") String region,
            @Value("${woodle.s3.accessKey:dummy}") String accessKey,
            @Value("${woodle.s3.secretKey:dummy}") String secretKey,
            @Value("${woodle.s3.pathStyle:true}") boolean pathStyle
    ) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());

        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public PollRepository pollRepository(
            S3Client s3Client,
            ObjectMapper objectMapper,
            @Value("${woodle.s3.bucket:woodle}") String bucketName
    ) {
        return new S3PollRepository(s3Client, objectMapper, bucketName);
    }
}
