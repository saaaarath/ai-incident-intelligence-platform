package com.aiincident.failure;

/**
 * Optional callback interface that a service can implement to receive lifecycle
 * notifications when a specific {@link FailureType} is enabled or disabled via
 * the generic control API.
 *
 * <p>Implementations are registered with {@link FailureInjectionService} and
 * called synchronously on each state change. Implementations must be thread-safe.
 */
public interface FailureLifecycleListener {

    /**
     * Called when the given failure type has been activated.
     *
     * @param type the failure type that was just enabled
     */
    void onFailureEnabled(FailureType type);

    /**
     * Called when failure injection has been disabled (type reset to NONE).
     */
    void onFailureDisabled();
}
