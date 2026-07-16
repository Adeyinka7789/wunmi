package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.FlagDisabledException;
import io.github.adeyinka7789.wunmi.FlagEngine;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * Enforces {@link RequiresFlag} on Spring beans: resolves the flag through {@link FlagEngine#isOn}
 * and throws {@link FlagDisabledException} before the method body runs when it is off. A
 * method-level annotation takes precedence over a type-level one.
 */
@Aspect
public class RequiresFlagAspect {

    private final FlagEngine flagEngine;

    public RequiresFlagAspect(FlagEngine flagEngine) {
        this.flagEngine = flagEngine;
    }

    @Before("@annotation(io.github.adeyinka7789.wunmi.spring.RequiresFlag) "
            + "|| @within(io.github.adeyinka7789.wunmi.spring.RequiresFlag)")
    public void enforce(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        RequiresFlag required = AnnotatedElementUtils.findMergedAnnotation(method, RequiresFlag.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(joinPoint.getTarget().getClass(), RequiresFlag.class);
        }
        if (required == null) {
            return;
        }
        if (!flagEngine.isOn(required.value())) {
            throw new FlagDisabledException(required.value());
        }
    }
}
