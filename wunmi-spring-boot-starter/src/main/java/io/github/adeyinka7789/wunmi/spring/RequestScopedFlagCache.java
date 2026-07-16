package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagCache;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.TtlFlagCache;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The default {@link FlagCache} for Spring apps. When an HTTP request is bound it caches in
 * request attributes — loaded once per request and discarded at its end, so a page with many
 * gated widgets hits the store once and always sees a consistent, fresh view. Outside a request
 * (jobs, schedulers) it falls back to a shared short-TTL cache ({@link TtlFlagCache}).
 */
public class RequestScopedFlagCache implements FlagCache {

    private static final String FLAGS_ATTR = "wunmi.flags";
    private static final String OVERRIDES_ATTR = "wunmi.overrides";

    private final FlagCache fallback;

    public RequestScopedFlagCache(long ttlMillis) {
        this.fallback = new TtlFlagCache(ttlMillis);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Flag> flags(Supplier<Map<String, Flag>> loader) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return fallback.flags(loader);
        }
        Map<String, Flag> cache = (Map<String, Flag>) attrs.getAttribute(FLAGS_ATTR, RequestAttributes.SCOPE_REQUEST);
        if (cache == null) {
            cache = loader.get();
            attrs.setAttribute(FLAGS_ATTR, cache, RequestAttributes.SCOPE_REQUEST);
        }
        return cache;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<FlagOverride> override(String key, Supplier<Optional<FlagOverride>> loader) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return fallback.override(key, loader);
        }
        Map<String, Optional<FlagOverride>> cache =
                (Map<String, Optional<FlagOverride>>) attrs.getAttribute(OVERRIDES_ATTR, RequestAttributes.SCOPE_REQUEST);
        if (cache == null) {
            cache = new HashMap<>();
            attrs.setAttribute(OVERRIDES_ATTR, cache, RequestAttributes.SCOPE_REQUEST);
        }
        return cache.computeIfAbsent(key, k -> loader.get());
    }

    @Override
    public void invalidate() {
        // Clear this request's view too, not just the shared fallback: a handler that changes a flag
        // and then reads it must see its own write. Outside a request (the change poller) there is
        // nothing bound and only the fallback applies.
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            attrs.removeAttribute(FLAGS_ATTR, RequestAttributes.SCOPE_REQUEST);
            attrs.removeAttribute(OVERRIDES_ATTR, RequestAttributes.SCOPE_REQUEST);
        }
        fallback.invalidate();
    }
}
