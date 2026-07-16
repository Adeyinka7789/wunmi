package io.github.adeyinka7789.wunmi.admin;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.FlagStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WunmiAdminControllerTest {

    private MockMvc mvc;
    private FlagEngine engine;

    @BeforeEach
    void setUp() {
        engine = new FlagEngine(new MapFlagStore());
        mvc = MockMvcBuilders.standaloneSetup(new WunmiAdminController(engine)).build();
    }

    @Test
    void servesTheHtmlPage() throws Exception {
        mvc.perform(get("/wunmi/admin"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("wunmi")))
                .andExpect(content().string(containsString("/wunmi/admin"))); // __BASE__ substituted
    }

    @Test
    void createThenListFlags() throws Exception {
        mvc.perform(post("/wunmi/admin/api/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DARK_MODE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("DARK_MODE"))
                .andExpect(jsonPath("$.enabled").value(true));

        mvc.perform(get("/wunmi/admin/api/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("DARK_MODE"));
    }

    @Test
    void disableAndSetRollout() throws Exception {
        engine.enable("DARK_MODE", "t");
        mvc.perform(post("/wunmi/admin/api/flags/DARK_MODE/disable"))
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put("/wunmi/admin/api/flags/DARK_MODE/rollout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percentage\":30}"))
                .andExpect(jsonPath("$.rolloutPercentage").value(30));
    }

    @Test
    void overrideRoundTrip() throws Exception {
        engine.enable("DARK_MODE", "t");

        String body = mvc.perform(post("/wunmi/admin/api/flags/DARK_MODE/overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"SUBJECT\",\"value\":\"user-1\",\"enabled\":false,\"reason\":\"opt-out\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("SUBJECT"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/wunmi/admin/api/flags/DARK_MODE/overrides"))
                .andExpect(jsonPath("$[0].value").value("user-1"));

        String id = body.replaceAll(".*\"id\":\"([0-9a-f-]+)\".*", "$1");
        mvc.perform(delete("/wunmi/admin/api/overrides/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/wunmi/admin/api/flags/DARK_MODE/overrides"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Minimal in-memory store for the controller test. */
    static class MapFlagStore implements FlagStore {
        private final Map<String, Flag> flags = new HashMap<>();
        private final Map<UUID, FlagOverride> overrides = new HashMap<>();

        @Override public Optional<Flag> findFlag(String name) { return Optional.ofNullable(flags.get(name)); }
        @Override public List<Flag> findAllFlags() { return new ArrayList<>(flags.values()); }
        @Override public Flag saveFlag(Flag flag) { flags.put(flag.name(), flag); return flag; }
        @Override public Optional<FlagOverride> findOverride(String f, FlagOverride.Scope s, String v) {
            return overrides.values().stream()
                    .filter(o -> o.flagName().equals(f) && o.scope() == s && o.value().equals(v)).findFirst();
        }
        @Override public List<FlagOverride> findOverrides(String f) {
            return overrides.values().stream().filter(o -> o.flagName().equals(f)).toList();
        }
        @Override public FlagOverride saveOverride(FlagOverride o) {
            UUID id = o.id() != null ? o.id() : UUID.randomUUID();
            FlagOverride p = new FlagOverride(id, o.flagName(), o.scope(), o.value(), o.enabled(), o.reason(), o.createdBy());
            overrides.put(id, p);
            return p;
        }
        @Override public Optional<FlagOverride> findOverrideById(UUID id) { return Optional.ofNullable(overrides.get(id)); }
        @Override public void deleteOverride(UUID id) { overrides.remove(id); }
    }
}
