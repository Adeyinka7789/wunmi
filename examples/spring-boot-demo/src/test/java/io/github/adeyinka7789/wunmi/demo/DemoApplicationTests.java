package io.github.adeyinka7789.wunmi.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end smoke test of the whole wunmi auto-configuration chain in one running app:
 * starter + JDBC store (over H2) + admin console + the {@code @RequiresFlag} aspect. If any of the
 * auto-configuration wiring regresses, this fails to boot.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void contextLoads() {
        // The @SpringBootTest bootstrap is itself the assertion: the full chain wired up and booted.
    }

    @Test
    void dashboardReadsSeededFlag() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string("new dashboard"));
    }

    @Test
    void adminApiListsSeededFlags() throws Exception {
        mvc.perform(get("/wunmi/admin/api/flags"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NEW_DASHBOARD")))
                .andExpect(content().string(containsString("BETA_EXPORT")));
    }

    @Test
    void requiresFlagGate_returns404_whenOffForSubject() throws Exception {
        // BETA_EXPORT is a 50% rollout; find a userId the consistent hash buckets OUT, and assert 404.
        String excluded = excludedSubject();
        mvc.perform(get("/api/export").param("userId", excluded))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresFlagGate_returns200_whenOnForSubject() throws Exception {
        String included = includedSubject();
        mvc.perform(get("/api/export").param("userId", included))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(included)));
    }

    private static String includedSubject() {
        return subjectWithBucket(true);
    }

    private static String excludedSubject() {
        return subjectWithBucket(false);
    }

    /** A userId whose rollout bucket for BETA_EXPORT is inside (or outside) the 50% cut. */
    private static String subjectWithBucket(boolean included) {
        for (int i = 0; i < 1000; i++) {
            String candidate = "user-" + i;
            int bucket = Math.floorMod((candidate + "BETA_EXPORT").hashCode(), 100);
            if ((bucket < 50) == included) {
                return candidate;
            }
        }
        throw new IllegalStateException("no matching subject found");
    }
}
