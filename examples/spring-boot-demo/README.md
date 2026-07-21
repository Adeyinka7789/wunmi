# wunmi example — Spring Boot

A runnable Spring Boot app wired with the full wunmi stack: the auto-configured starter, the
bundled JDBC `FlagStore` over in-memory H2, and the admin console. It's also the project's
end-to-end auto-configuration test.

## Run it

```bash
mvn -pl examples/spring-boot-demo -am spring-boot:run
```

Two flags are seeded at startup — `NEW_DASHBOARD` (fully on) and `BETA_EXPORT` (50% rollout).

| Try | What it shows |
|-----|----------------|
| `GET http://localhost:8080/api/dashboard` | Imperative `flags.isOn(...)` — returns `new dashboard`. |
| `GET http://localhost:8080/api/export?userId=alice` | `@RequiresFlag` with a SpEL subject; 404 when `alice` is outside the rollout. Try different ids. |
| `GET http://localhost:8080/wunmi/admin` | The admin console — flip flags, set rollout, add per-subject/segment overrides. |
| `GET http://localhost:8080/h2-console` | Inspect the `wunmi_*` tables (JDBC url `jdbc:h2:mem:wunmi-demo`). |

The demo's `FlagContextResolver` reads `X-User-Id` / `X-User-Plan` request headers so you can see
subject overrides and rollout bucketing take effect without a login:

```bash
curl -H "X-User-Id: alice" -H "X-User-Plan: enterprise" http://localhost:8080/api/dashboard
```

## Gating the admin console

Uncomment `wunmi.admin.require-role` in `application.yml` and add Spring Security to require an
authority on `/wunmi/admin/**`. Without a required role set, secure the path with your own config.
