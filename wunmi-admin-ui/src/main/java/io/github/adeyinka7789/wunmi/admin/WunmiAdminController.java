package io.github.adeyinka7789.wunmi.admin;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Self-contained admin console for wunmi, mounted at {@value #BASE_PATH}: a small JSON API over
 * the {@link FlagEngine} management operations plus one dependency-free HTML page ({@code GET
 * /wunmi/admin}).
 *
 * <p><b>Security is the host application's responsibility</b> — this controller performs no
 * authorization. Protect {@code /wunmi/admin/**} with your own security config. The actor recorded
 * for each change is the request's {@link Principal} name, or {@code "wunmi-admin"} if none.
 */
@RestController
@RequestMapping(WunmiAdminController.BASE_PATH)
public class WunmiAdminController {

    public static final String BASE_PATH = "/wunmi/admin";
    private static final String PAGE_RESOURCE = "io/github/adeyinka7789/wunmi/admin/admin.html";

    private final FlagEngine engine;
    private final WunmiAdminMetadata metadata; // nullable — host may not describe its domain
    private volatile String pageHtml;

    /** Console with no host metadata: generic SUBJECT/SEGMENT labels and free-text inputs. */
    public WunmiAdminController(FlagEngine engine) {
        this(engine, null);
    }

    public WunmiAdminController(FlagEngine engine, WunmiAdminMetadata metadata) {
        this.engine = engine;
        this.metadata = metadata;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html());
    }

    /**
     * Host-provided domain metadata for the overrides UI. Absent a {@link WunmiAdminMetadata}
     * bean this returns generic defaults, and the console renders free-text value inputs.
     */
    @GetMapping("/api/metadata")
    public MetadataView metadata() {
        if (metadata == null) {
            return new MetadataView("Subject", "Segment", List.of(), false);
        }
        return new MetadataView(metadata.subjectLabel(), metadata.segmentLabel(),
                metadata.segments(), metadata.supportsSubjectSearch());
    }

    /** Typeahead source for SUBJECT overrides; empty unless the host supports subject search. */
    @GetMapping("/api/subjects")
    public List<WunmiAdminMetadata.Option> subjects(@RequestParam("q") String query) {
        if (metadata == null || !metadata.supportsSubjectSearch() || query == null || query.isBlank()) {
            return List.of();
        }
        return metadata.searchSubjects(query.trim());
    }

    @GetMapping("/api/flags")
    public List<Flag> flags() {
        return engine.listFlags();
    }

    @PostMapping("/api/flags")
    public Flag create(@RequestBody NameRequest request, HttpServletRequest http) {
        return engine.enable(request.name(), actor(http));
    }

    @PostMapping("/api/flags/{name}/enable")
    public Flag enable(@PathVariable("name") String name, HttpServletRequest http) {
        return engine.enable(name, actor(http));
    }

    @PostMapping("/api/flags/{name}/disable")
    public Flag disable(@PathVariable("name") String name, HttpServletRequest http) {
        return engine.disable(name, actor(http));
    }

    @PutMapping("/api/flags/{name}/rollout")
    public Flag rollout(@PathVariable("name") String name, @RequestBody RolloutRequest request, HttpServletRequest http) {
        return engine.setRollout(name, request.percentage(), actor(http));
    }

    @GetMapping("/api/flags/{name}/overrides")
    public List<FlagOverride> overrides(@PathVariable("name") String name) {
        return engine.listOverrides(name);
    }

    @PostMapping("/api/flags/{name}/overrides")
    public FlagOverride putOverride(@PathVariable("name") String name, @RequestBody OverrideRequest request,
                                    HttpServletRequest http) {
        return engine.putOverride(name, request.scope(), request.value(),
                request.enabled(), request.reason(), actor(http));
    }

    @DeleteMapping("/api/overrides/{id}")
    public ResponseEntity<Void> removeOverride(@PathVariable("id") UUID id, HttpServletRequest http) {
        engine.removeOverride(id, actor(http));
        return ResponseEntity.noContent().build();
    }

    private String actor(HttpServletRequest http) {
        Principal principal = http.getUserPrincipal();
        return principal != null ? principal.getName() : "wunmi-admin";
    }

    private String html() {
        String cached = pageHtml;
        if (cached == null) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(PAGE_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("admin.html resource not found: " + PAGE_RESOURCE);
                }
                cached = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("__BASE__", BASE_PATH);
                pageHtml = cached;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load admin.html", e);
            }
        }
        return cached;
    }

    /** Shape returned by {@code GET /api/metadata} — what the console needs to render pickers. */
    public record MetadataView(String subjectLabel, String segmentLabel,
                               List<WunmiAdminMetadata.Option> segments, boolean subjectSearch) { }

    public record NameRequest(String name) { }

    public record RolloutRequest(int percentage) { }

    public record OverrideRequest(Scope scope, String value, boolean enabled, String reason) { }
}
