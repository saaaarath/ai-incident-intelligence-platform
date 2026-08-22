package com.aiincident.failure.pool;

/**
 * Thrown when the simulated connection pool is fully exhausted and no connection
 * can be acquired within the allowed timeout. Extends {@link RuntimeException}
 * so that Spring's exception-translation mechanism (via {@link org.springframework.dao.support.PersistenceExceptionTranslator})
 * can wrap it in a {@link org.springframework.dao.DataAccessResourceFailureException}
 * when it propagates through a JPA/JDBC repository call.
 */
public class ConnectionPoolExhaustedException extends RuntimeException {

    public ConnectionPoolExhaustedException(String message) {
        super(message);
    }

    public ConnectionPoolExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
