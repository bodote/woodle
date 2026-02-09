package io.github.bodote.woodle.adapter.in.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.testfixtures.TestFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = PollViewController.class, properties = "woodle.public-base-url=https://woodle.click")
@DisplayName("/poll/{id} with configured public base url")
class PollViewControllerPublicBaseUrlTest {

    @MockitoBean
    private ReadPollUseCase readPollUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("uses configured public base URL for share links")
    void usesConfiguredPublicBaseUrlForShareLinks() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000017");
        String adminSecret = "AdminSecretCfg";
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );
        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret)
                        .with(request -> {
                            request.setScheme("http");
                            request.setServerName("internal-lb");
                            request.setServerPort(8080);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://woodle.click/poll/" + pollId)))
                .andExpect(content().string(containsString("https://woodle.click/poll/" + pollId + "-" + adminSecret)));
    }
}
