/**
 * wunmi — a small, robust feature-flag engine for Java.
 *
 * <p>Layered resolution (global kill switch → per-subject/segment overrides → percentage rollout)
 * over five pluggable SPIs, with no framework dependency (only SLF4J).
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * enum Feature implements FlagKey { DARK_MODE; public String key() { return name(); } }
 *
 * FlagEngine flags = new FlagEngine(myFlagStore);   // minimal wiring
 * if (flags.isOn(Feature.DARK_MODE)) { ... }
 * }</pre>
 *
 * <h2>The pieces</h2>
 * <ul>
 *   <li>{@link io.github.adeyinka7789.wunmi.FlagEngine} — resolution + management.</li>
 *   <li>{@link io.github.adeyinka7789.wunmi.FlagKey} — a typed handle your enum implements.</li>
 *   <li>SPIs you provide: {@link io.github.adeyinka7789.wunmi.FlagStore} (persistence),
 *       {@link io.github.adeyinka7789.wunmi.FlagCache} (caching — or {@code NONE}/{@link
 *       io.github.adeyinka7789.wunmi.TtlFlagCache}), {@link io.github.adeyinka7789.wunmi.FlagAuditListener}
 *       (audit — or {@code NOOP}), {@link io.github.adeyinka7789.wunmi.FlagContextResolver}
 *       (who is asking — or {@code EMPTY}), {@link io.github.adeyinka7789.wunmi.FlagEvaluationListener}
 *       (meter each evaluation — or {@code NOOP}).</li>
 *   <li>Model: {@link io.github.adeyinka7789.wunmi.Flag},
 *       {@link io.github.adeyinka7789.wunmi.FlagOverride},
 *       {@link io.github.adeyinka7789.wunmi.FlagContext}.</li>
 * </ul>
 *
 * <p>Spring Boot users: add {@code wunmi-spring-boot-starter} for auto-configuration, a
 * request-scoped cache, and the {@code @RequiresFlag} method gate.
 */
package io.github.adeyinka7789.wunmi;
