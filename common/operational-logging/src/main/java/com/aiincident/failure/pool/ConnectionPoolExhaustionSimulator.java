package com.aiincident.failure.pool;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates database connection pool exhaustion by tracking available "slots" with
 * a Semaphore. This bean does NOT replace or wrap the real DataSource — instead
 * the service layer checks pool availability before executing repository calls,
 * causing a real {@link ConnectionPoolExhaustedException} to propagate through
 * the request when the simulated pool has no capacity.
 *
 * <p>This design avoids Spring DataSource circular-dependency issues while still
 * producing genuine failures that affect request processing (not just log output).
 */
public class ConnectionPoolExhaustionSimulator {

    private static final int NORMAL_POOL_SIZE = 10;
    private static final long ACQUIRE_TIMEOUT_MS = 2_000L;

    private final AtomicBoolean exhausted = new AtomicBoolean(false);
    private final AtomicInteger availableConnections = new AtomicInteger(NORMAL_POOL_SIZE);

    /** Guards access when exhaustion is active. */
    private volatile Semaphore semaphore = new Semaphore(NORMAL_POOL_SIZE, true);

    /**
     * Enable connection pool exhaustion simulation.
     *
     * @param available the number of permits (connections) still allowed through;
     *                  0 means fully exhausted — every acquisition attempt will time out
     */
    public void enableExhaustion(int available) {
        int permits = Math.max(0, available);
        availableConnections.set(permits);
        this.semaphore = new Semaphore(permits, true);
        exhausted.set(true);
    }

    /** Disable simulation and restore normal (unlimited) behavior. */
    public void disableExhaustion() {
        exhausted.set(false);
        this.semaphore = new Semaphore(NORMAL_POOL_SIZE, true);
        availableConnections.set(NORMAL_POOL_SIZE);
    }

    /** Returns {@code true} when exhaustion mode is currently active. */
    public boolean isExhausted() {
        return exhausted.get();
    }

    public int getAvailableConnections() {
        return availableConnections.get();
    }

    /**
     * Attempt to "acquire a connection" from the simulated pool.
     *
     * <p>When exhaustion is active and no permits are available within the timeout,
     * throws {@link ConnectionPoolExhaustedException}. When exhaustion is disabled
     * this is a no-op.
     *
     * @throws ConnectionPoolExhaustedException if no connection can be acquired
     *                                           within the configured timeout
     */
    public void acquireConnection() {
        if (!exhausted.get()) {
            return;
        }
        Semaphore current = semaphore;
        try {
            boolean acquired = current.tryAcquire(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ConnectionPoolExhaustedException(
                        "Simulated connection pool exhaustion: no connections available after "
                                + ACQUIRE_TIMEOUT_MS + " ms (pool size=" + availableConnections.get() + ")");
            }
            // Release immediately — we only simulate the contention/timeout, not lifecycle.
            current.release();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConnectionPoolExhaustedException(
                    "Simulated connection pool exhaustion: thread interrupted while waiting for connection");
        }
    }
}
