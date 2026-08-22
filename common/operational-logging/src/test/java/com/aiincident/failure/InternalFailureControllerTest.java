package com.aiincident.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalFailureControllerTest {

    private MockMvc mockMvc;
    private FailureInjectionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .build();
        service = new FailureInjectionService(
                true,
                "test-secret",
                "NONE",
                3000L,
                "test-service",
                objectMapper);
        InternalFailureController controller = new InternalFailureController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejectsAccessWhenTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/internal/failures")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getsStatusWhenTokenIsValid() throws Exception {
        mockMvc.perform(get("/internal/failures")
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.type").value("NONE"));
    }

    @Test
    void enablesAndDisablesFailureMode() throws Exception {
        // Enable DB_FAILURE
        mockMvc.perform(post("/internal/failures")
                        .header("X-Internal-Token", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DB_FAILURE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.type").value("DB_FAILURE"));

        assertThat(service.isFailureActive()).isTrue();

        // Disable via DELETE
        mockMvc.perform(delete("/internal/failures")
                        .header("X-Internal-Token", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.type").value("NONE"));

        assertThat(service.isFailureActive()).isFalse();
    }
}
