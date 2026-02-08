package io.github.bodote.woodle.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Undefined route fallback")
class UndefinedRouteRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("redirects undefined GET to /poll/new")
    void redirectsUndefinedGetToStartPage() throws Exception {
        mockMvc.perform(get("/definitely-not-defined"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));
    }

    @Test
    @DisplayName("redirects undefined POST to /poll/new")
    void redirectsUndefinedPostToStartPage() throws Exception {
        mockMvc.perform(post("/definitely-not-defined"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));
    }

    @Test
    @DisplayName("redirects undefined PUT to /poll/new")
    void redirectsUndefinedPutToStartPage() throws Exception {
        mockMvc.perform(put("/definitely-not-defined"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));
    }

    @Test
    @DisplayName("redirects undefined DELETE to /poll/new")
    void redirectsUndefinedDeleteToStartPage() throws Exception {
        mockMvc.perform(delete("/definitely-not-defined"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/poll/new"));
    }
}
