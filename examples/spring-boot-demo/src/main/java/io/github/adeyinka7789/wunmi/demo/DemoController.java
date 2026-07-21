package io.github.adeyinka7789.wunmi.demo;

import io.github.adeyinka7789.wunmi.FlagDisabledException;
import io.github.adeyinka7789.wunmi.FlagEngine;
import io.github.adeyinka7789.wunmi.spring.RequiresFlag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Two endpoints showing the two ways to check a flag: imperatively, and via {@code @RequiresFlag}. */
@RestController
public class DemoController {

    private final FlagEngine flags;

    public DemoController(FlagEngine flags) {
        this.flags = flags;
    }

    /** Imperative check against the ambient context (see {@link DemoConfig#flagContextResolver()}). */
    @GetMapping("/api/dashboard")
    public String dashboard() {
        return flags.isOn(DemoFlags.NEW_DASHBOARD) ? "new dashboard" : "classic dashboard";
    }

    /**
     * Declarative gate. The subject is pulled from the {@code userId} argument via SpEL, so this
     * needs no ambient context — the flag resolves (overrides + 50% rollout) for that user.
     */
    @RequiresFlag(value = "BETA_EXPORT", subject = "#userId")
    @GetMapping("/api/export")
    public String export(@RequestParam String userId) {
        return "exported data for " + userId;
    }

    /** Turn a disabled-flag rejection into a 404, the usual "feature doesn't exist" response. */
    @ExceptionHandler(FlagDisabledException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String featureOff() {
        return "not found";
    }
}
