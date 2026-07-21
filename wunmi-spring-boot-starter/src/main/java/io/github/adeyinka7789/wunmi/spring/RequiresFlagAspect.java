package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.FlagDisabledException;
import io.github.adeyinka7789.wunmi.FlagEngine;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces {@link RequiresFlag} on Spring beans: resolves the flag through {@link FlagEngine} and
 * throws {@link FlagDisabledException} before the method body runs when it is off. A method-level
 * annotation takes precedence over a type-level one.
 *
 * <p>When {@link RequiresFlag#subject()} or {@link RequiresFlag#segment()} is set, those SpEL
 * expressions are evaluated over the method arguments and resolution uses that explicit context;
 * otherwise the flag resolves against the ambient
 * {@link io.github.adeyinka7789.wunmi.FlagContextResolver}.
 */
@Aspect
public class RequiresFlagAspect {

    private final FlagEngine flagEngine;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

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
        if (!isOn(required, joinPoint, method)) {
            throw new FlagDisabledException(required.value());
        }
    }

    private boolean isOn(RequiresFlag required, JoinPoint joinPoint, Method method) {
        boolean hasSubject = !required.subject().isEmpty();
        boolean hasSegment = !required.segment().isEmpty();
        if (!hasSubject && !hasSegment) {
            return flagEngine.isOn(required.value());
        }
        EvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        String subject = hasSubject ? evaluate(required.subject(), context) : null;
        String segment = hasSegment ? evaluate(required.segment(), context) : null;
        return flagEngine.resolve(required.value(), subject, segment);
    }

    private String evaluate(String expression, EvaluationContext context) {
        Expression parsed = expressionCache.computeIfAbsent(expression, expressionParser::parseExpression);
        Object value = parsed.getValue(context);
        return value == null ? null : value.toString();
    }
}
