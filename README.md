# wunmi

A small, robust feature-flag engine for Java. Kill switches, per-subject and per-segment
overrides, and percentage rollouts — over four pluggable SPIs, with no framework dependency in
the core (only SLF4J).

- **`wunmi-core`** — the engine. Pure Java, framework-free.
- **`wunmi-jdbc`** — a ready-made `FlagStore` over any JDBC database, with a portable schema.
- **`wunmi-spring-boot-starter`** — auto-configuration, a request-scoped cache, and a
  `@RequiresFlag` method gate for Spring Boot apps.
- **`wunmi-admin-ui`** — an optional, self-contained admin console (`/wunmi/admin`).

Java 17+.

## How resolution works

A flag is evaluated top-to-bottom; the first layer that applies wins:

1. **No such flag** → off (fail-closed).
2. **Global kill switch off** → off, absolutely (no override can revive it).
3. **A `SUBJECT` override** for the current subject → its value.
4. **A `SEGMENT` override** for the current segment → its value.
5. **Rollout < 100%** (needs a subject) → consistent-hash bucket test.
6. Otherwise → on.

## Quick start (core)

```java
enum Feature implements FlagKey {
    DARK_MODE, BETA_CHECKOUT;
    public String key() { return name(); }
}

// Implement FlagStore over your database (or use an in-memory map).
FlagEngine flags = new FlagEngine(myFlagStore);   // minimal wiring: no cache, no context

if (flags.isOn(Feature.DARK_MODE)) {
    // ...
}
```

With a context (enables per-subject/segment overrides and rollout):

```java
FlagContextResolver ctx = () -> new FlagContext(currentUserId(), currentUserPlan());
FlagEngine flags = new FlagEngine(myFlagStore, new TtlFlagCache(5000),
                                  FlagAuditListener.NOOP, ctx);

flags.isOn(Feature.BETA_CHECKOUT);   // resolves for the current user + plan
```

Manage flags:

```java
flags.enable("DARK_MODE", "admin@acme.com");
flags.setRollout("BETA_CHECKOUT", 25, "admin@acme.com");                 // 25% of subjects
flags.putOverride("BETA_CHECKOUT", Scope.SUBJECT, userId, true, "VIP", "admin@acme.com");
```

## Persistence

Implement `FlagStore` over your own database, or use the bundled **`wunmi-jdbc`** — a
`FlagStore` over any `DataSource`, no ORM:

```java
WunmiSchema.initialize(dataSource);              // idempotent CREATE TABLE IF NOT EXISTS
FlagStore store = new JdbcFlagStore(dataSource);
```

## Spring Boot

Add the starter. If `wunmi-jdbc` is on the classpath and you have a `DataSource`, a store is
auto-configured — so with a datasource you need **zero** persistence code:

```xml
<dependency>
    <groupId>io.github.adeyinka7789</groupId>
    <artifactId>wunmi-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.adeyinka7789</groupId>
    <artifactId>wunmi-jdbc</artifactId>
    <version>0.1.0</version>
</dependency>
```

```properties
wunmi.jdbc.initialize-schema=true   # create the tables at startup (dev / first run)
```

Or supply your own store instead by declaring a `FlagStore` bean. Either way, declare a
`FlagContextResolver` bean to enable per-subject/segment overrides and rollout:

```java
@Bean
FlagContextResolver flagContext() {
    return () -> new FlagContext(CurrentUser.id(), CurrentUser.plan());
}
```

You then get a `FlagEngine` bean and the method gate:

```java
@RequiresFlag("BETA_CHECKOUT")           // throws FlagDisabledException (map to 404) when off
public Receipt checkout(Cart cart) { ... }
```

Defaults (override by declaring your own bean):

| SPI | Default |
|-----|---------|
| `FlagCache` | `RequestScopedFlagCache` — request-scoped, short-TTL fallback (`wunmi.cache-ttl-ms`, default 5000) |
| `FlagContextResolver` | `FlagContext.EMPTY` (global resolution only) |
| `FlagAuditListener` | no-op |
| `FlagChangeBroadcaster` | `JdbcFlagChangeBroadcaster` when `wunmi-jdbc` + a `DataSource` are present, else none |

## Running more than one instance

By default a flag change only reaches other instances when their cache TTL lapses. With `wunmi-jdbc`
on the classpath you get cross-instance invalidation for free: a change bumps a version counter in
the database you already have, every instance polls it, and a bump clears their caches — no Redis or
Kafka required.

```properties
wunmi.invalidation.enabled=true            # default; set false to skip the poller entirely
wunmi.invalidation.poll-interval-ms=5000   # default; bounds how long a peer's change takes to land
```

This needs the `wunmi_flag_version` table (included in the bundled schema). If it's missing — say you
manage the schema yourself and haven't added it — wunmi logs a warning at startup and falls back to
TTL convergence rather than failing to boot.

For broker-backed fan-out instead of polling, declare your own `FlagChangeBroadcaster` bean: call
your listeners when a peer's message arrives, and publish from `broadcastChange()`.

## Admin console

Add `wunmi-admin-ui` and a dependency-free management page appears at **`/wunmi/admin`**
(list/add flags, toggle, set rollout, add/remove subject & segment overrides) over a small JSON
API. It has no auth of its own — **secure `/wunmi/admin/**` behind your own security config.**

```xml
<dependency>
    <groupId>io.github.adeyinka7789</groupId>
    <artifactId>wunmi-admin-ui</artifactId>
    <version>0.1.0</version>
</dependency>
```

## The SPIs

| SPI | You implement it to… | Bundled defaults |
|-----|----------------------|------------------|
| `FlagStore` | persist flags + overrides | `JdbcFlagStore` (module `wunmi-jdbc`) |
| `FlagCache` | cache reads | `FlagCache.NONE`, `TtlFlagCache`, `RequestScopedFlagCache` (Spring) |
| `FlagAuditListener` | record changes | `FlagAuditListener.NOOP` |
| `FlagContextResolver` | say who is asking | `FlagContextResolver.EMPTY` |

## License

Apache License 2.0 — see [LICENSE](LICENSE).
