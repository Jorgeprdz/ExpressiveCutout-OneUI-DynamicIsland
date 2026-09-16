package com.ekoehler.expressivecutout.overlay

/**
 * Stable key for primary content handoffs. Mutable metadata stays on the same key, while a genuine
 * visual identity replacement gets a new key and therefore a short content transition.
 */
internal fun primaryContentKey(event: IslandEvent?): String? = event?.visualIdentity
