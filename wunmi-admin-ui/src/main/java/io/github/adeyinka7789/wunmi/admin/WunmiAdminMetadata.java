package io.github.adeyinka7789.wunmi.admin;

import java.util.List;

/**
 * Optional host-provided description of what a flag override's SUBJECT and SEGMENT
 * <em>mean</em> in this application, so the admin console can render domain-appropriate
 * pickers instead of raw text inputs.
 *
 * <p>wunmi is domain-agnostic: an override targets a {@code SUBJECT} (one identity — a user,
 * tenant, or customer id) or a {@code SEGMENT} (a group — a plan, cohort, or region), and the
 * override's value is just a string matched against the {@code FlagContext} at evaluation time.
 * The console therefore cannot, on its own, know that "SUBJECT" means "Tenant" here or that the
 * valid segments are your subscription plans.
 *
 * <p>Provide a bean implementing this interface and the console will label the scopes with your
 * terms, offer your {@link #segments()} as a dropdown, and (when {@link #supportsSubjectSearch()}
 * is true) offer a typeahead backed by {@link #searchSubjects(String)}. With no such bean the
 * console falls back to generic {@code SUBJECT}/{@code SEGMENT} labels and free-text value inputs,
 * so this is purely additive — nothing breaks when it is absent.
 */
public interface WunmiAdminMetadata {

    /** Human label for the SUBJECT scope (e.g. {@code "Tenant"}). Defaults to {@code "Subject"}. */
    default String subjectLabel() {
        return "Subject";
    }

    /** Human label for the SEGMENT scope (e.g. {@code "Plan"}). Defaults to {@code "Segment"}. */
    default String segmentLabel() {
        return "Segment";
    }

    /**
     * The selectable SEGMENT values, e.g. subscription plans. When empty, the console shows a
     * free-text segment field. Each option's {@code value} is what gets stored on the override.
     */
    default List<Option> segments() {
        return List.of();
    }

    /**
     * Whether SUBJECT typeahead search is available. When true the console renders a search box
     * wired to {@link #searchSubjects(String)}; otherwise it shows a free-text subject field.
     */
    default boolean supportsSubjectSearch() {
        return false;
    }

    /**
     * Typeahead lookup for SUBJECT overrides — return subjects whose label matches {@code query}.
     * Each option's {@code value} is the id stored on the override (e.g. a tenant UUID) and
     * {@code label} is what the operator sees (e.g. a company name). Only consulted when
     * {@link #supportsSubjectSearch()} is true. Implementations should cap the result size.
     */
    default List<Option> searchSubjects(String query) {
        return List.of();
    }

    /** A selectable value: {@code value} is persisted on the override, {@code label} is displayed. */
    record Option(String value, String label) { }
}
