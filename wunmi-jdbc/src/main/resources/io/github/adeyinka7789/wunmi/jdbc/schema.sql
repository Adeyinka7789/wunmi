-- wunmi JDBC schema. Portable across H2 / PostgreSQL / MySQL (generic types,
-- CREATE TABLE IF NOT EXISTS, UUIDs stored as VARCHAR). Executed by WunmiSchema.

CREATE TABLE IF NOT EXISTS wunmi_flags (
    name               VARCHAR(200) PRIMARY KEY,
    enabled            BOOLEAN NOT NULL,
    description        VARCHAR(1000),
    rollout_percentage INT NOT NULL,
    updated_by         VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS wunmi_flag_overrides (
    id             VARCHAR(36) PRIMARY KEY,
    flag_name      VARCHAR(200) NOT NULL,
    scope          VARCHAR(20) NOT NULL,
    override_value VARCHAR(400) NOT NULL,
    enabled        BOOLEAN NOT NULL,
    reason         VARCHAR(1000),
    created_by     VARCHAR(200),
    CONSTRAINT uq_wunmi_override UNIQUE (flag_name, scope, override_value)
);

-- Single-row generation counter for cross-instance cache invalidation
-- (JdbcFlagChangeBroadcaster). Bumped on every write; peers poll it.
CREATE TABLE IF NOT EXISTS wunmi_flag_version (
    id      INT PRIMARY KEY,
    version BIGINT NOT NULL
);
