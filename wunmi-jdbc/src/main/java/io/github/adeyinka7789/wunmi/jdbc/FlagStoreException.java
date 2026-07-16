package io.github.adeyinka7789.wunmi.jdbc;

/** Unchecked wrapper for {@link java.sql.SQLException}s raised by {@link JdbcFlagStore}. */
public class FlagStoreException extends RuntimeException {

    public FlagStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
