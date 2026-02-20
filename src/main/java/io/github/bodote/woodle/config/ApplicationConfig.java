package io.github.bodote.woodle.config;

import io.github.bodote.woodle.adapter.out.email.NoopPollEmailSender;
import io.github.bodote.woodle.adapter.out.email.SesPollEmailSender;
import io.github.bodote.woodle.adapter.out.email.SmtpPollEmailSender;
import io.github.bodote.woodle.adapter.out.persistence.InMemoryPollRepository;
import io.github.bodote.woodle.adapter.out.persistence.InMemoryWizardStateRepository;
import io.github.bodote.woodle.adapter.out.persistence.S3PollRepository;
import io.github.bodote.woodle.adapter.out.persistence.S3WizardStateRepository;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.util.Properties;

@Configuration
public class ApplicationConfig {

    @Bean
    @ConditionalOnMissingBean(PollRepository.class)
    public PollRepository pollRepository(
            @Value("${woodle.s3.enabled:false}") boolean s3Enabled,
            @Value("${woodle.s3.bucket:woodle}") String bucketName,
            @Value("${woodle.poll.schema-version:1}") String pollSchemaVersion,
            ObjectProvider<S3Client> s3ClientProvider,
            ObjectMapper objectMapper
    ) {
        if (s3Enabled) {
            S3Client s3Client = s3ClientProvider.getIfAvailable();
            if (s3Client == null) {
                throw new IllegalStateException("S3 is enabled but no S3 client bean is available");
            }
            return new S3PollRepository(s3Client, objectMapper, bucketName, pollSchemaVersion);
        }
        return new InMemoryPollRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WizardStateRepository.class)
    public WizardStateRepository wizardStateRepository(
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
            return new S3WizardStateRepository(s3Client, objectMapper, bucketName);
        }
        return new InMemoryWizardStateRepository();
    }

    @Bean
    public PollEmailSender pollEmailSender(
            @Value("${woodle.email.enabled:false}") boolean emailEnabled,
            @Value("${woodle.email.provider:ses}") String emailProvider,
            @Value("${woodle.email.from:noreply@woodle.click}") String fromAddress,
            @Value("${woodle.email.subject-prefix:}") String subjectPrefix,
            @Value("${woodle.public-base-url:}") String publicBaseUrl,
            ObjectProvider<SesV2Client> sesV2ClientProvider,
            ObjectProvider<JavaMailSender> javaMailSenderProvider
    ) {
        if (!emailEnabled) {
            return new NoopPollEmailSender();
        }

        String provider = emailProvider == null ? "ses" : emailProvider.trim().toLowerCase();
        if ("smtp".equals(provider)) {
            JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
            if (javaMailSender == null) {
                throw new IllegalStateException("Email provider smtp is enabled but no JavaMailSender bean is available");
            }
            return new SmtpPollEmailSender(javaMailSender, fromAddress, subjectPrefix, publicBaseUrl);
        }
        if ("ses".equals(provider)) {
            SesV2Client sesV2Client = sesV2ClientProvider.getIfAvailable();
            if (sesV2Client == null) {
                throw new IllegalStateException("Email provider ses is enabled but no SesV2Client bean is available");
            }
            return new SesPollEmailSender(sesV2Client, fromAddress, subjectPrefix, publicBaseUrl);
        }
        throw new IllegalStateException("Unsupported email provider: " + provider);
    }

    @Bean
    @ConditionalOnMissingBean(SesV2Client.class)
    @ConditionalOnProperty(name = "woodle.email.provider", havingValue = "ses", matchIfMissing = true)
    public SesV2Client sesV2Client() {
        return SesV2Client.create();
    }

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    @ConditionalOnProperty(name = "woodle.email.provider", havingValue = "smtp")
    public JavaMailSender javaMailSender(
            @Value("${woodle.email.smtp.host:}") String host,
            @Value("${woodle.email.smtp.port:587}") int port,
            @Value("${woodle.email.smtp.username:}") String username,
            @Value("${woodle.email.smtp.password:}") String password
    ) {
        if (host.isBlank()) {
            throw new IllegalStateException("Email provider smtp requires property woodle.email.smtp.host");
        }
        if (username.isBlank()) {
            throw new IllegalStateException("Email provider smtp requires property woodle.email.smtp.username");
        }
        if (password.isBlank()) {
            throw new IllegalStateException("Email provider smtp requires property woodle.email.smtp.password");
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");

        return mailSender;
    }

    @Bean
    public CreatePollUseCase createPollUseCase(
            PollRepository pollRepository,
            PollEmailSender pollEmailSender,
            @Value("${woodle.email.enabled:false}") boolean emailEnabled
    ) {
        return new CreatePollService(pollRepository, pollEmailSender, emailEnabled);
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
