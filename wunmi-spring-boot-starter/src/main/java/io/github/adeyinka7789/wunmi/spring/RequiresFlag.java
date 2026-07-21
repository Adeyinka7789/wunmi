package io.github.adeyinka7789.wunmi.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Gates a method (or every method of a type) behind a feature flag. {@link RequiresFlagAspect}
 * intercepts annotated beans and throws
 * {@link io.github.adeyinka7789.wunmi.FlagDisabledException} before the method runs when the flag
 * is not on for the current context.
 *
 * <pre>{@code
 * @RequiresFlag("BETA_CHECKOUT")
 * public String checkout() { ... }
 * }</pre>
 *
 * <p>By default the flag resolves against the ambient {@link io.github.adeyinka7789.wunmi.FlagContext}
 * from your {@link io.github.adeyinka7789.wunmi.FlagContextResolver}. Alternatively, target the
 * check inline with SpEL over the method arguments — no resolver bean needed:
 *
 * <pre>{@code
 * @RequiresFlag(value = "BETA_CHECKOUT", subject = "#user.id", segment = "#user.plan")
 * public Receipt checkout(User user, Cart cart) { ... }
 * }</pre>
 *
 * The expressions evaluate against the invocation, so method parameters are referenced by name
 * ({@code #user}) — or positionally ({@code #a0}, {@code #p0}) when parameter names aren't
 * retained. A {@code null} result skips that layer (segment) or the rollout (subject), exactly as
 * a {@code null} {@code FlagContext} field would.
 *
 * <p>A method-level annotation overrides a type-level one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresFlag {

    /** The flag key ({@code FlagKey.key()}, e.g. the enum constant name). */
    String value();

    /**
     * Optional SpEL expression, evaluated over the method arguments, giving the subject id to
     * resolve against (drives subject overrides and rollout bucketing). When set, the ambient
     * {@link io.github.adeyinka7789.wunmi.FlagContextResolver} is bypassed. Empty = unset.
     */
    String subject() default "";

    /**
     * Optional SpEL expression, evaluated over the method arguments, giving the segment to resolve
     * against (drives segment overrides). When either {@link #subject()} or this is set, the
     * ambient {@link io.github.adeyinka7789.wunmi.FlagContextResolver} is bypassed. Empty = unset.
     */
    String segment() default "";
}
