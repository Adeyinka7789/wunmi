package io.github.adeyinka7789.wunmi.demo;

import io.github.adeyinka7789.wunmi.FlagKey;

/** The demo's feature flags as a typed enum — the recommended way to reference wunmi flags. */
public enum DemoFlags implements FlagKey {

    NEW_DASHBOARD,
    BETA_EXPORT;

    @Override
    public String key() {
        return name();
    }
}
