package com.ekoehler.expressivecutout.notifications.live

/**
 * Platform evidence that an observed notification belongs to Android's promoted-ongoing system.
 * [Status.PROMOTED] is authoritative; promotable/requested states are deliberately secondary.
 */
data class NativeLiveEvidence private constructor(
    val status: Status,
    val promoted: Boolean,
    val promotable: Boolean,
    val requested: Boolean,
) {
    /** Whether an ongoing, non-clearable notification may pass the ordinary plumbing filter. */
    val shouldBypassOngoingFilter: Boolean
        get() = status != Status.UNAVAILABLE && status != Status.NONE

    /** Strength of the framework evidence available for this notification. */
    enum class Status {
        UNAVAILABLE,
        NONE,
        REQUESTED,
        PROMOTABLE,
        PROMOTED,
    }

    companion object {
        /** Builds evidence without touching API-36 classes, making platform gating unit-testable. */
        fun classify(
            platformAvailable: Boolean,
            promotedFlag: Boolean,
            promotable: Boolean,
            requested: Boolean,
        ): NativeLiveEvidence {
            if (!platformAvailable) {
                return NativeLiveEvidence(
                    status = Status.UNAVAILABLE,
                    promoted = false,
                    promotable = false,
                    requested = false,
                )
            }
            val status = when {
                promotedFlag -> Status.PROMOTED
                promotable -> Status.PROMOTABLE
                requested -> Status.REQUESTED
                else -> Status.NONE
            }
            return NativeLiveEvidence(
                status = status,
                promoted = promotedFlag,
                promotable = promotable,
                requested = requested,
            )
        }
    }
}
