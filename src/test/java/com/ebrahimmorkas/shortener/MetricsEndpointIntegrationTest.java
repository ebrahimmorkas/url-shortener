package com.ebrahimmorkas.shortener;

import com.ebrahimmorkas.shortener.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureObservability
class MetricsEndpointIntegrationTest extends IntegrationTest {

    @Test
    void prometheusEndpointExposesBusinessAndLatencyMetrics() throws Exception {
        String code = shorten("https://example.com/metrics");
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("shortener_redirects_total")))
                .andExpect(content().string(containsString("shortener_cache_lookups_total")))
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")))
                .andExpect(content().string(containsString("application=\"url-shortener\"")));
    }
}
