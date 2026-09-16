package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import java.util.Locale

/**
 * Scores local notification evidence into conservative live-activity semantics. Matching is fully
 * offline and returns null when evidence is weak or competing domains are too close to call.
 */
class SemanticLiveActivityClassifier {

    /** Classifies [signals] without retaining or logging any of its private notification content. */
    fun classify(signals: NotificationLiveSignals): ParsedLiveCandidate? {
        val text = SearchText.from(signals)
        if (text.normalized.isEmpty() && !signals.hasStructuredProgress() && !signals.countdown) {
            return classifyNativeFallback(signals)
        }

        val scored = listOfNotNull(
            classifyOtp(signals, text),
            classifyRide(signals, text),
            classifyFood(signals, text),
            classifyParcel(signals, text),
            classifyWeather(signals, text),
            classifyTimer(signals, text),
            classifyProgress(signals, text),
        ).sortedByDescending { it.confidence }

        val best = scored.firstOrNull() ?: return classifyNativeFallback(signals)
        val runnerUp = scored.getOrNull(1)
        if (runnerUp != null && best.confidence - runnerUp.confidence < MIN_WINNING_MARGIN) {
            return null
        }
        return best
    }

    /** Recognizes short-lived verification codes only when both numeric and verification evidence exist. */
    private fun classifyOtp(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val otpNumber = OTP_NUMBER.find(text.raw)?.groupValues?.getOrNull(1) ?: return null
        if (otpNumber.length !in OTP_MIN_DIGITS..OTP_MAX_DIGITS) return null
        if (text.hasAny(OTP_EXCLUSIONS) || CURRENCY_NUMBER.containsMatchIn(text.raw)) return null

        var score = 0
        val evidence = linkedSetOf<String>()
        if (text.hasAny(OTP_CONTEXT_TERMS)) {
            score += 4
            evidence += "OTP_CONTEXT"
        }
        if (text.hasAny(OTP_CODE_TERMS)) {
            score += 2
            evidence += "OTP_CODE_TERM"
        }
        score += 3
        evidence += "OTP_SHORT_NUMBER"
        if (signals.ongoing) score += 1
        if (score < OTP_THRESHOLD) return null

        return ParsedLiveCandidate(
            kind = LiveActivity.Kind.OTP,
            phase = "verification",
            confidence = score,
            evidenceCodes = evidence,
            lifecycle = LiveActivity.Lifecycle.TRANSIENT,
            ttlMs = OTP_TTL_MS,
            isSensitive = true,
        )
    }

    /** Recognizes an active rideshare trip from actor plus movement/ETA evidence. */
    private fun classifyRide(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val actor = text.hasAny(RIDE_ACTOR_TERMS)
        val motion = text.hasAny(RIDE_MOTION_TERMS)
        val eta = ETA_PATTERN.containsMatchIn(text.normalized)
        val action = text.hasAny(RIDE_ACTION_TERMS)
        if (!actor || (!motion && !eta && !action)) return null

        var score = 0
        val evidence = linkedSetOf<String>()
        if (actor) {
            score += 3
            evidence += "RIDE_ACTOR"
        }
        if (motion) {
            score += 3
            evidence += "RIDE_ACTIVE_STATE"
        }
        if (eta) {
            score += 2
            evidence += "TEMPORAL_ETA"
        }
        if (action) {
            score += 2
            evidence += "RIDE_ACTION"
        }
        if (signals.ongoing) score += 1
        if (text.hasAny(PROMOTIONAL_TERMS)) score -= PROMOTION_PENALTY
        if (score < DOMAIN_THRESHOLD) return null

        return domainCandidate(LiveActivity.Kind.RIDESHARE, "arriving", score, evidence)
    }

    /** Recognizes an active prepared/in-transit food order rather than order advertising or receipts. */
    private fun classifyFood(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val order = text.hasAny(FOOD_ORDER_TERMS)
        val active = text.hasAny(FOOD_ACTIVE_TERMS)
        if (!order || !active) return null

        var score = 6
        val evidence = linkedSetOf("FOOD_ORDER", "FOOD_ACTIVE_STATE")
        if (ETA_PATTERN.containsMatchIn(text.normalized)) {
            score += 2
            evidence += "TEMPORAL_ETA"
        }
        if (text.hasAny(FOOD_CONTEXT_TERMS)) {
            score += 1
            evidence += "FOOD_CONTEXT"
        }
        if (signals.ongoing) score += 1
        if (text.hasAny(PROMOTIONAL_TERMS)) score -= PROMOTION_PENALTY
        if (score < DOMAIN_THRESHOLD) return null

        return domainCandidate(LiveActivity.Kind.FOOD_ORDER, "on_the_way", score, evidence)
    }

    /** Recognizes a physical shipment being tracked through an active delivery state. */
    private fun classifyParcel(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val shipment = text.hasAny(PARCEL_TERMS)
        val active = text.hasAny(PARCEL_ACTIVE_TERMS)
        if (!shipment || !active) return null

        var score = 6
        val evidence = linkedSetOf("PARCEL_OBJECT", "PARCEL_ACTIVE_STATE")
        if (ETA_PATTERN.containsMatchIn(text.normalized) || STOPS_PATTERN.containsMatchIn(text.normalized)) {
            score += 2
            evidence += "PARCEL_DISTANCE_OR_ETA"
        }
        if (signals.ongoing) score += 1
        if (text.hasAny(PROMOTIONAL_TERMS)) score -= PROMOTION_PENALTY
        if (score < DOMAIN_THRESHOLD) return null

        return domainCandidate(LiveActivity.Kind.PARCEL_DELIVERY, "out_for_delivery", score, evidence)
    }

    /** Recognizes imminent weather changes, excluding forecasts and probability-only notifications. */
    private fun classifyWeather(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val weather = text.hasAny(WEATHER_TERMS)
        val imminent = text.hasAny(WEATHER_IMMINENT_TERMS)
        if (!weather || !imminent) return null

        var score = 6
        val evidence = linkedSetOf("WEATHER_EVENT", "WEATHER_IMMINENT")
        if (ETA_PATTERN.containsMatchIn(text.normalized)) {
            score += 2
            evidence += "TEMPORAL_ETA"
        }
        if (signals.ongoing) score += 1
        if (score < DOMAIN_THRESHOLD) return null

        return ParsedLiveCandidate(
            kind = LiveActivity.Kind.WEATHER,
            phase = "imminent",
            confidence = score,
            evidenceCodes = evidence,
            lifecycle = LiveActivity.Lifecycle.TRANSIENT,
            ttlMs = WEATHER_TTL_MS,
        )
    }

    /** Recognizes structured countdowns while leaving richer timer handling to the specialized parser. */
    private fun classifyTimer(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val timerContext = text.hasAny(TIMER_TERMS)
        val score = when {
            signals.countdown -> 9
            signals.chronometer && timerContext -> 8
            else -> return null
        }
        val evidence = if (signals.countdown) {
            setOf("COUNTDOWN_SIGNAL")
        } else {
            setOf("CHRONOMETER_SIGNAL", "TIMER_CONTEXT")
        }
        return ParsedLiveCandidate(
            kind = LiveActivity.Kind.TIMER,
            phase = "countdown",
            confidence = score,
            evidenceCodes = evidence,
            lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        )
    }

    /** Recognizes structured progress first and permits textual percentages only with operation context. */
    private fun classifyProgress(signals: NotificationLiveSignals, text: SearchText): ParsedLiveCandidate? {
        val structured = signals.hasStructuredProgress()
        val operation = text.hasAny(PROGRESS_OPERATION_TERMS)
        val percent = PERCENT_PATTERN.find(text.normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (!structured && !(operation && percent != null)) return null

        var score = 0
        val evidence = linkedSetOf<String>()
        if (signals.hasProgressStyle) {
            score += 4
            evidence += "PROGRESS_STYLE"
        }
        if (signals.progressIndeterminate) {
            score += 2
            evidence += "PROGRESS_INDETERMINATE"
        }
        if (signals.progressMax != null && signals.progressMax > 0 && signals.progressCurrent != null) {
            score += 2
            evidence += "PROGRESS_STRUCTURED"
        }
        if (operation) {
            score += 3
            evidence += "PROGRESS_OPERATION"
        }
        if (percent != null && operation) {
            score += 3
            evidence += "PROGRESS_PERCENT"
        }
        if (signals.ongoing) score += 1
        if (score < PROGRESS_THRESHOLD) return null

        val progress = structuredProgress(signals) ?: percent?.let {
            LiveActivity.Progress(current = it.coerceIn(0, 100), max = 100)
        }
        val complete = progress?.let { !it.isIndeterminate && it.max > 0 && it.current >= it.max } == true
        return ParsedLiveCandidate(
            kind = LiveActivity.Kind.GENERIC_PROGRESS,
            phase = if (complete) "complete" else "progress",
            confidence = score,
            evidenceCodes = evidence,
            lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
            progress = progress,
        )
    }

    /** Preserves native live evidence as a generic activity only when no semantic class won. */
    private fun classifyNativeFallback(signals: NotificationLiveSignals): ParsedLiveCandidate? {
        val evidence = signals.nativeEvidence ?: return null
        if (!evidence.shouldBypassOngoingFilter) return null
        return ParsedLiveCandidate(
            kind = LiveActivity.Kind.NOTIFICATION,
            phase = "live",
            confidence = NATIVE_FALLBACK_CONFIDENCE,
            evidenceCodes = setOf("NATIVE_LIVE_EVIDENCE"),
            lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
        )
    }

    /** Builds a standard until-removed domain candidate. */
    private fun domainCandidate(
        kind: LiveActivity.Kind,
        phase: String,
        score: Int,
        evidence: Set<String>,
    ): ParsedLiveCandidate = ParsedLiveCandidate(
        kind = kind,
        phase = phase,
        confidence = score,
        evidenceCodes = evidence,
        lifecycle = LiveActivity.Lifecycle.UNTIL_REMOVED,
    )

    /** Converts structured notification progress into the neutral live-activity progress model. */
    private fun structuredProgress(signals: NotificationLiveSignals): LiveActivity.Progress? {
        if (signals.progressIndeterminate) {
            return LiveActivity.Progress(
                current = signals.progressCurrent?.coerceAtLeast(0) ?: 0,
                max = signals.progressMax?.coerceAtLeast(0) ?: 0,
                isIndeterminate = true,
            )
        }
        val max = signals.progressMax?.takeIf { it > 0 } ?: return null
        val current = signals.progressCurrent ?: return null
        return LiveActivity.Progress(current = current.coerceIn(0, max), max = max)
    }

    /** Reports whether framework metadata proves a real determinate or indeterminate progress state. */
    private fun NotificationLiveSignals.hasStructuredProgress(): Boolean =
        hasProgressStyle || progressIndeterminate || (progressMax != null && progressMax > 0 && progressCurrent != null)

    /** Normalized searchable text derived only from the current in-memory notification. */
    private data class SearchText(
        val normalized: String,
        val raw: String,
    ) {
        /** Checks complete normalized words or phrases rather than arbitrary substrings. */
        fun hasAny(terms: Set<String>): Boolean {
            val padded = " $normalized "
            return terms.any { term -> padded.contains(" $term ") }
        }

        companion object {
            /** Builds searchable text from presentation strings and action labels without retaining it globally. */
            fun from(signals: NotificationLiveSignals): SearchText {
                val pieces = buildList {
                    add(signals.title)
                    add(signals.text)
                    add(signals.subText)
                    add(signals.infoText)
                    add(signals.category)
                    addAll(signals.additionalText)
                    addAll(signals.actionLabels)
                }.filterNotNull().filter { it.isNotBlank() }
                return SearchText(
                    normalized = pieces.joinToString(" ") { NotificationTextNormalizer.normalize(it) }
                        .replace(Regex("\\s+"), " ")
                        .trim(),
                    raw = pieces.joinToString(" ").lowercase(Locale.ROOT),
                )
            }
        }
    }

    private companion object {
        /** Classifier thresholds, TTLs, regexes, and normalized vocabularies are centralized here. */
        const val DOMAIN_THRESHOLD = 7
        const val OTP_THRESHOLD = 8
        const val PROGRESS_THRESHOLD = 6
        const val MIN_WINNING_MARGIN = 2
        const val PROMOTION_PENALTY = 4
        const val NATIVE_FALLBACK_CONFIDENCE = 10
        const val OTP_MIN_DIGITS = 4
        const val OTP_MAX_DIGITS = 8
        const val OTP_TTL_MS = 5 * 60 * 1_000L
        const val WEATHER_TTL_MS = 30 * 60 * 1_000L

        val ETA_PATTERN = Regex("(?<!\\d)\\d{1,3}\\s*(?:min|mins|minute|minutes|minuto|minutos)\\b")
        val STOPS_PATTERN = Regex("(?<!\\d)\\d{1,3}\\s*(?:stop|stops|parada|paradas)\\b")
        val PERCENT_PATTERN = Regex("(?<!\\d)(\\d{1,3})\\s*%")
        val OTP_NUMBER = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")
        val CURRENCY_NUMBER = Regex("[$€£]\\s*\\d{4,8}(?!\\d)")

        val PROMOTIONAL_TERMS = setOf(
            "discount", "descuento", "offer", "oferta", "promotion", "promocion", "promo",
            "free delivery", "entrega gratis", "favorite", "favorito",
        )
        val RIDE_ACTOR_TERMS = setOf("driver", "conductor", "conductora", "chofer")
        val RIDE_MOTION_TERMS = setOf(
            "arriving", "arrives", "arrival", "llega", "llegando", "pickup", "recogida",
            "vehicle", "vehiculo", "plate", "placa", "trip", "viaje",
        )
        val RIDE_ACTION_TERMS = setOf("contact driver", "contactar conductor", "call driver", "llamar conductor")
        val FOOD_ORDER_TERMS = setOf("order", "pedido")
        val FOOD_ACTIVE_TERMS = setOf(
            "on the way", "en camino", "preparing", "preparando", "arriving", "llegando", "llega",
        )
        val FOOD_CONTEXT_TERMS = setOf("restaurant", "restaurante", "courier", "repartidor", "delivery", "entrega")
        val PARCEL_TERMS = setOf("package", "paquete", "shipment", "envio", "tracking", "rastreo")
        val PARCEL_ACTIVE_TERMS = setOf(
            "out for delivery", "en reparto", "stops away", "paradas", "carrier", "paqueteria",
            "delivered", "entregado",
        )
        val WEATHER_TERMS = setOf("rain", "lluvia", "storm", "tormenta", "snow", "nieve", "hail", "granizo")
        val WEATHER_IMMINENT_TERMS = setOf(
            "starts", "starting", "comienza", "comenzando", "approaching", "se acerca", "acercandose",
            "ends", "ending", "termina", "terminando",
        )
        val OTP_CONTEXT_TERMS = setOf(
            "verification", "verificacion", "one time", "one time password", "otp", "login",
            "inicio de sesion", "security code", "codigo de seguridad",
        )
        val OTP_CODE_TERMS = setOf("code", "codigo", "password", "contrasena")
        val OTP_EXCLUSIONS = setOf("postal code", "codigo postal", "zip code", "order number", "numero de pedido")
        val TIMER_TERMS = setOf("timer", "temporizador", "countdown", "cuenta regresiva")
        val PROGRESS_OPERATION_TERMS = setOf(
            "download", "downloading", "descarga", "descargando", "upload", "uploading", "subiendo",
            "update", "updating", "actualizacion", "actualizando", "install", "installing", "instalando",
            "backup", "backing up", "respaldo", "sync", "syncing", "sincronizando",
        )
    }
}
