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
 * A method-level annotation overrides a type-level one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequiresFlag {

    /** The flag key ({@code FlagKey.key()}, e.g. the enum constant name). */
    String value();
}
