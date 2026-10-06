package com.spendio.sharedkernel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The request-id contract (ARCHITECTURE.md D.16, E.1): generate a safe id, honour a
 * valid incoming one for correlation, expose it in the MDC during the request only.
 */
class RequestIdFilterTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void generatesRequestIdWhenAbsent() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void echoesValidIncomingRequestId() throws Exception {
        mockMvc.perform(get("/probe").header(RequestIdFilter.REQUEST_ID_HEADER, "client-abc.123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "client-abc.123"));
    }

    @Test
    void replacesInvalidIncomingRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "not a valid id!"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")))
                .andReturn();

        assertThat(result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo("not a valid id!");
    }

    @Test
    void replacesOversizedIncomingRequestId() throws Exception {
        String oversized = "a".repeat(65);
        mockMvc.perform(get("/probe").header(RequestIdFilter.REQUEST_ID_HEADER, oversized))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void mdcIsVisibleDuringRequestAndClearedAfter() throws Exception {
        MvcResult result = mockMvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andReturn();

        String requestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("mdc=" + requestId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe")
        String probe() {
            return "mdc=" + MDC.get(RequestIdFilter.MDC_KEY);
        }
    }
}
