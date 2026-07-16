package io.github.adeyinka7789.wunmi;

import io.github.adeyinka7789.wunmi.FlagAuditListener.FlagChange;
import io.github.adeyinka7789.wunmi.FlagOverride.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The feature-flag engine: resolves whether a flag is on and manages flag/override state.
 * Framework-free — it depends only on the four SPIs ({@link FlagStore}, {@link FlagCache},
 * {@link FlagAuditListener}, {@link FlagContextResolver}) and SLF4J.
 *
 * <h2>Resolution order</h2>
 * {@link #resolve(String, String, String)} short-circuits on the first layer that applies:
 * <ol>
 *   <li>no stored flag → {@code false} (fail-closed)</li>
 *   <li>global kill switch off → {@code false} (absolute; no override can revive it)</li>
 *   <li>a matching {@code SUBJECT} override → its value</li>
 *   <li>a matching {@code SEGMENT} override → its value</li>
 *   <li>rollout &lt; 100 (needs a subject) → consistent-hash bucket test</li>
 *   <li>otherwise → {@code true}</li>
 * </ol>
 *
 * <h2>Entry points</h2>
 * {@link #isOn(FlagKey)} resolves against the current {@link FlagContext};
 * {@link #isEnabled(FlagKey)} checks only the global switch (no context);
 * {@link #resolve(String, String, String)} takes an explicit context.
 */
public final class FlagEngine {

    private static final Logger log = LoggerFactory.getLogger(FlagEngine.class);

    private final FlagStore store;
    private final FlagCache cache;
    private final FlagAuditListener audit;
    private final FlagContextResolver contextResolver;

    public FlagEngine(FlagStore store, FlagCache cache, FlagAuditListener audit,
                      FlagContextResolver contextResolver) {
        this.store = requireNonNull(store, "store");
        this.cache = requireNonNull(cache, "cache");
        this.audit = requireNonNull(audit, "audit");
        this.contextResolver = requireNonNull(contextResolver, "contextResolver");
    }

    /** Minimal wiring: your {@link FlagStore}, no caching, no audit, no context resolver. */
    public FlagEngine(FlagStore store) {
        this(store, FlagCache.NONE, FlagAuditListener.NOOP, FlagContextResolver.EMPTY);
    }

    // ── Resolution ─────────────────────────────────────────────────────────────

    /** Whether the flag is on for the current {@link FlagContext}. */
    public boolean isOn(FlagKey key) {
        return isOn(key.key());
    }

    /** {@link #isOn(FlagKey)} by raw name. */
    public boolean isOn(String flagName) {
        FlagContext ctx = contextResolver.currentContext();
        return resolve(flagName, ctx.subjectId(), ctx.segment());
    }

    /** Whether the flag's global switch is on, ignoring subject/segment/rollout. */
    public boolean isEnabled(FlagKey key) {
        return isEnabled(key.key());
    }

    /** {@link #isEnabled(FlagKey)} by raw name. */
    public boolean isEnabled(String flagName) {
        return resolve(flagName, null, null);
    }

    /**
     * The full layered resolution against an explicit context. {@code subjectId} and
     * {@code segment} may be {@code null}, in which case the corresponding override layers
     * (and rollout, which needs a subject) are skipped.
     */
    public boolean resolve(String flagName, String subjectId, String segment) {
        Flag flag = cache.flags(this::loadAllFlags).get(flagName);
        if (flag == null) {
            log.warn("Feature flag '{}' is not registered; defaulting to disabled", flagName);
            return false;
        }
        if (!flag.enabled()) {
            return false;
        }
        if (subjectId != null) {
            Optional<FlagOverride> o = cachedOverride(flagName, Scope.SUBJECT, subjectId);
            if (o.isPresent()) {
                return o.get().enabled();
            }
        }
        if (segment != null) {
            Optional<FlagOverride> o = cachedOverride(flagName, Scope.SEGMENT, segment);
            if (o.isPresent()) {
                return o.get().enabled();
            }
        }
        int rollout = flag.rolloutPercentage();
        if (subjectId != null && rollout < 100) {
            int bucket = Math.floorMod((subjectId + flagName).hashCode(), 100);
            return bucket < rollout;
        }
        return true;
    }

    /** Throw {@link FlagDisabledException} unless the flag is on for the current context. */
    public void require(FlagKey key) {
        if (!isOn(key)) {
            throw new FlagDisabledException(key.key());
        }
    }

    // ── Management ─────────────────────────────────────────────────────────────

    public Flag enable(String flagName, String actor) {
        return setEnabled(flagName, true, actor);
    }

    public Flag disable(String flagName, String actor) {
        return setEnabled(flagName, false, actor);
    }

    /** Set the rollout percentage (0–100), creating the flag if absent. */
    public Flag setRollout(String flagName, int percentage, String actor) {
        Flag flag = flagOrNew(flagName).withRolloutPercentage(percentage, actor);
        Flag saved = store.saveFlag(flag);
        log.info("Flag '{}' rollout set to {}% by {}", flagName, percentage, actor);
        audit.onFlagChanged(new FlagChange(flagName, actor, "rollout=" + percentage + "%"));
        return saved;
    }

    public List<Flag> listFlags() {
        return store.findAllFlags();
    }

    public List<FlagOverride> listOverrides(String flagName) {
        return store.findOverrides(flagName);
    }

    /**
     * Create or update the override for the given flag/scope/value. Ensures the flag exists
     * first (resolution bails at the missing-flag gate before consulting overrides, so an
     * override on an unregistered flag would otherwise be a silent no-op).
     */
    public FlagOverride putOverride(String flagName, Scope scope, String value,
                                    boolean enabled, String reason, String actor) {
        ensureFlagExists(flagName);
        FlagOverride toSave = store.findOverride(flagName, scope, value)
                .map(existing -> existing.withState(enabled, reason, actor))
                .orElseGet(() -> FlagOverride.create(flagName, scope, value, enabled, reason, actor));
        FlagOverride saved = store.saveOverride(toSave);
        log.info("Flag '{}' override [{} {}] set to enabled={} by {}", flagName, scope, value, enabled, actor);
        audit.onFlagChanged(new FlagChange(flagName, actor,
                "override " + scope + "=" + value + " enabled=" + enabled));
        return saved;
    }

    public void removeOverride(UUID overrideId, String actor) {
        FlagOverride existing = store.findOverrideById(overrideId).orElse(null);
        store.deleteOverride(overrideId);
        if (existing != null) {
            audit.onFlagChanged(new FlagChange(existing.flagName(), actor,
                    "override removed " + existing.scope() + "=" + existing.value()));
        }
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private Flag setEnabled(String flagName, boolean enabled, String actor) {
        Flag flag = flagOrNew(flagName).withEnabled(enabled, actor);
        Flag saved = store.saveFlag(flag);
        log.info("Flag '{}' set to enabled={} by {}", flagName, enabled, actor);
        audit.onFlagChanged(new FlagChange(flagName, actor, "enabled=" + enabled));
        return saved;
    }

    private void ensureFlagExists(String flagName) {
        if (store.findFlag(flagName).isEmpty()) {
            store.saveFlag(Flag.enabledFlag(flagName));
            log.info("Auto-created flag '{}' (enabled) to back a new override", flagName);
        }
    }

    private Flag flagOrNew(String flagName) {
        return store.findFlag(flagName).orElseGet(() -> Flag.enabledFlag(flagName));
    }

    private Map<String, Flag> loadAllFlags() {
        return store.findAllFlags().stream().collect(Collectors.toMap(Flag::name, f -> f));
    }

    private Optional<FlagOverride> cachedOverride(String flagName, Scope scope, String value) {
        String key = flagName + '|' + scope + '|' + value;
        return cache.override(key, () -> store.findOverride(flagName, scope, value));
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
