package com.example.recoveryshield.domain.model

/**
 * Deterministic states for the Blockify voluntary protection lifecycle.
 */
enum class ProtectionState {
    /** App installed for the first time; onboarding not yet completed. */
    FIRST_RUN,

    /** Setup in progress; user needs to review terms and configure system. */
    SETUP_REQUIRED,

    /** One or more required permissions (such as Accessibility) have not been enabled. */
    PERMISSION_REQUIRED,

    /** All permissions are granted, but protection has not been voluntarily activated yet. */
    READY_BUT_INACTIVE,

    /** Full protection is active: UI signals are monitored locally for explicit content. */
    ACTIVE,

    /** High-confidence explicit content was detected; intervention UI and navigation in progress. */
    INTERVENTION,

    /** User initiated a disable request; cooling-off period initialization. */
    COOLDOWN_REQUESTED,

    /** Cooling-off period is running; protection remains fully active. */
    COOLDOWN_ACTIVE,

    /** Cooling-off timer expired; user must deliberately confirm to disable protection. */
    DISABLE_CONFIRMATION,

    /** Protection is voluntarily disabled. */
    DISABLED,

    /**
     * Required capability (e.g. Accessibility Service) was unexpectedly lost or degraded.
     * The app refuses to falsely report active protection.
     */
    DEGRADED,

    /** System or database error occurred; recovery action required. */
    ERROR
}
