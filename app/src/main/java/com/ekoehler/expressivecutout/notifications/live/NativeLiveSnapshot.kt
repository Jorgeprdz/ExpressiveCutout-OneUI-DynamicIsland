package com.ekoehler.expressivecutout.notifications.live

import android.graphics.drawable.Icon

/** Framework-neutral-enough snapshot of Android live-update state before it is mapped to the island domain. */
data class NativeLiveSnapshot(
    val evidence: NativeLiveEvidence,
    val styleKind: StyleKind,
    val progress: Progress? = null,
) {
    /** Public notification style understood by this compile SDK. */
    enum class StyleKind {
        PROGRESS,
        OTHER,
    }

    /** ProgressStyle data copied out of API-36 framework objects. */
    data class Progress(
        val current: Int,
        val max: Int,
        val indeterminate: Boolean,
        val styledByProgress: Boolean,
        val segments: List<Segment>,
        val points: List<Point>,
        val startIcon: Icon?,
        val trackerIcon: Icon?,
        val endIcon: Icon?,
    )

    /** One neutral progress-bar segment. */
    data class Segment(
        val id: Int,
        val length: Int,
        val color: Int,
    )

    /** One neutral progress milestone. */
    data class Point(
        val id: Int,
        val position: Int,
        val color: Int,
    )
}
