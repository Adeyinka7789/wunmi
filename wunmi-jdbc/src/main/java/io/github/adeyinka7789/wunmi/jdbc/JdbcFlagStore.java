package io.github.adeyinka7789.wunmi.jdbc;

import io.github.adeyinka7789.wunmi.Flag;
import io.github.adeyinka7789.wunmi.FlagOverride;
import io.github.adeyinka7789.wunmi.FlagStore;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link FlagStore} backed by plain JDBC over a {@link DataSource} — no Spring, no ORM. Works
 * with any JDBC database; the bundled {@link WunmiSchema} creates the two tables
 * ({@code wunmi_flags}, {@code wunmi_flag_overrides}) with portable types.
 *
 * <p>Upserts are done as UPDATE-then-INSERT so no database-specific {@code ON CONFLICT}/{@code
 * MERGE} syntax is needed. UUIDs are stored as their string form for portability.
 */
public class JdbcFlagStore implements FlagStore {

    private final DataSource dataSource;

    public JdbcFlagStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ── Flags ──────────────────────────────────────────────────────────────────

    @Override
    public Optional<Flag> findFlag(String name) {
        return querySingle("SELECT * FROM wunmi_flags WHERE name = ?", ps -> ps.setString(1, name), this::mapFlag);
    }

    @Override
    public List<Flag> findAllFlags() {
        return queryList("SELECT * FROM wunmi_flags", ps -> { }, this::mapFlag);
    }

    @Override
    public Flag saveFlag(Flag flag) {
        try (Connection c = dataSource.getConnection()) {
            boolean updated;
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE wunmi_flags SET enabled = ?, description = ?, rollout_percentage = ?, updated_by = ? WHERE name = ?")) {
                up.setBoolean(1, flag.enabled());
                up.setString(2, flag.description());
                up.setInt(3, flag.rolloutPercentage());
                up.setString(4, flag.updatedBy());
                up.setString(5, flag.name());
                updated = up.executeUpdate() > 0;
            }
            if (!updated) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO wunmi_flags (name, enabled, description, rollout_percentage, updated_by) VALUES (?, ?, ?, ?, ?)")) {
                    ins.setString(1, flag.name());
                    ins.setBoolean(2, flag.enabled());
                    ins.setString(3, flag.description());
                    ins.setInt(4, flag.rolloutPercentage());
                    ins.setString(5, flag.updatedBy());
                    ins.executeUpdate();
                }
            }
            return flag;
        } catch (SQLException e) {
            throw new FlagStoreException("saveFlag failed for '" + flag.name() + "'", e);
        }
    }

    // ── Overrides ──────────────────────────────────────────────────────────────

    @Override
    public Optional<FlagOverride> findOverride(String flagName, FlagOverride.Scope scope, String value) {
        return querySingle("SELECT * FROM wunmi_flag_overrides WHERE flag_name = ? AND scope = ? AND override_value = ?",
                ps -> {
                    ps.setString(1, flagName);
                    ps.setString(2, scope.name());
                    ps.setString(3, value);
                }, this::mapOverride);
    }

    @Override
    public List<FlagOverride> findOverrides(String flagName) {
        return queryList("SELECT * FROM wunmi_flag_overrides WHERE flag_name = ?",
                ps -> ps.setString(1, flagName), this::mapOverride);
    }

    @Override
    public FlagOverride saveOverride(FlagOverride override) {
        UUID id = override.id() != null ? override.id() : UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            boolean updated;
            try (PreparedStatement up = c.prepareStatement(
                    "UPDATE wunmi_flag_overrides SET flag_name = ?, scope = ?, override_value = ?, enabled = ?, reason = ?, created_by = ? WHERE id = ?")) {
                up.setString(1, override.flagName());
                up.setString(2, override.scope().name());
                up.setString(3, override.value());
                up.setBoolean(4, override.enabled());
                up.setString(5, override.reason());
                up.setString(6, override.createdBy());
                up.setString(7, id.toString());
                updated = up.executeUpdate() > 0;
            }
            if (!updated) {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO wunmi_flag_overrides (id, flag_name, scope, override_value, enabled, reason, created_by) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ins.setString(1, id.toString());
                    ins.setString(2, override.flagName());
                    ins.setString(3, override.scope().name());
                    ins.setString(4, override.value());
                    ins.setBoolean(5, override.enabled());
                    ins.setString(6, override.reason());
                    ins.setString(7, override.createdBy());
                    ins.executeUpdate();
                }
            }
            return new FlagOverride(id, override.flagName(), override.scope(), override.value(),
                    override.enabled(), override.reason(), override.createdBy());
        } catch (SQLException e) {
            throw new FlagStoreException("saveOverride failed for '" + override.flagName() + "'", e);
        }
    }

    @Override
    public Optional<FlagOverride> findOverrideById(UUID id) {
        return querySingle("SELECT * FROM wunmi_flag_overrides WHERE id = ?",
                ps -> ps.setString(1, id.toString()), this::mapOverride);
    }

    @Override
    public void deleteOverride(UUID id) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM wunmi_flag_overrides WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new FlagStoreException("deleteOverride failed for " + id, e);
        }
    }

    // ── plumbing ───────────────────────────────────────────────────────────────

    private Flag mapFlag(ResultSet rs) throws SQLException {
        return new Flag(rs.getString("name"), rs.getBoolean("enabled"), rs.getString("description"),
                rs.getInt("rollout_percentage"), rs.getString("updated_by"));
    }

    private FlagOverride mapOverride(ResultSet rs) throws SQLException {
        return new FlagOverride(UUID.fromString(rs.getString("id")), rs.getString("flag_name"),
                FlagOverride.Scope.valueOf(rs.getString("scope")), rs.getString("override_value"),
                rs.getBoolean("enabled"), rs.getString("reason"), rs.getString("created_by"));
    }

    private <T> Optional<T> querySingle(String sql, StatementBinder binder, RowMapper<T> mapper) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FlagStoreException("query failed: " + sql, e);
        }
    }

    private <T> List<T> queryList(String sql, StatementBinder binder, RowMapper<T> mapper) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new FlagStoreException("query failed: " + sql, e);
        }
    }

    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
}
