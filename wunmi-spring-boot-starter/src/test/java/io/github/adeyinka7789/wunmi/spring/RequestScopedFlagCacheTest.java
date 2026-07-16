package io.github.adeyinka7789.wunmi.spring;

import io.github.adeyinka7789.wunmi.Flag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RequestScopedFlagCacheTest {

    /** Request scope, without dragging the servlet API into this module for one stub. */
    private static final class MapRequestAttributes implements RequestAttributes {
        private final Map<String, Object> attrs = new HashMap<>();

        @Override public Object getAttribute(String name, int scope) { return attrs.get(name); }
        @Override public void setAttribute(String name, Object value, int scope) { attrs.put(name, value); }
        @Override public void removeAttribute(String name, int scope) { attrs.remove(name); }
        @Override public String[] getAttributeNames(int scope) { return attrs.keySet().toArray(new String[0]); }
        @Override public void registerDestructionCallback(String name, Runnable cb, int scope) { }
        @Override public Object resolveReference(String key) { return null; }
        @Override public String getSessionId() { return "test-session"; }
        @Override public Object getSessionMutex() { return this; }
    }

    private final RequestScopedFlagCache cache = new RequestScopedFlagCache(5000);

    private static Map<String, Flag> oneFlag() {
        return Map.of("F", Flag.enabledFlag("F"));
    }

    private static void bindRequest() {
        RequestContextHolder.setRequestAttributes(new MapRequestAttributes());
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void withinARequest_loadsOnce() {
        bindRequest();
        AtomicInteger loads = new AtomicInteger();

        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });

        assertThat(loads).hasValue(1);
    }

    /** A handler that changes a flag must see its own write, not the view cached before it. */
    @Test
    void invalidate_dropsTheCurrentRequestsFlags() {
        bindRequest();
        AtomicInteger loads = new AtomicInteger();

        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });
        cache.invalidate();
        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });

        assertThat(loads).hasValue(2);
    }

    @Test
    void invalidate_dropsTheCurrentRequestsOverrides() {
        bindRequest();
        AtomicInteger loads = new AtomicInteger();

        cache.override("k", () -> { loads.incrementAndGet(); return Optional.empty(); });
        cache.invalidate();
        cache.override("k", () -> { loads.incrementAndGet(); return Optional.empty(); });

        assertThat(loads).hasValue(2);
    }

    /** The change poller invalidates from a background thread, where no request is bound. */
    @Test
    void invalidate_withoutARequest_clearsTheFallbackAndDoesNotThrow() {
        AtomicInteger loads = new AtomicInteger();

        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });   // TTL fallback
        cache.invalidate();
        cache.flags(() -> { loads.incrementAndGet(); return oneFlag(); });

        assertThat(loads).as("the shared fallback should reload despite an unexpired TTL").hasValue(2);
    }
}
