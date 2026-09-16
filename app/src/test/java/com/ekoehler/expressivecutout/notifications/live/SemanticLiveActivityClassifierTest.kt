package com.ekoehler.expressivecutout.notifications.live

import com.ekoehler.expressivecutout.core.live.LiveActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies conservative EN/ES semantic classification and explicit ambiguity rejection. */
class SemanticLiveActivityClassifierTest {

    private val classifier = SemanticLiveActivityClassifier()

    @Test
    fun `classifies rideshare in english and spanish`() {
        assertKind(LiveActivity.Kind.RIDESHARE, signals(text = "Your driver is arriving in 3 min"))
        assertKind(LiveActivity.Kind.RIDESHARE, signals(text = "Tu conductor llega en 3 min"))
    }

    @Test
    fun `classifies accented title-only rideshare`() {
        assertKind(
            LiveActivity.Kind.RIDESHARE,
            signals(title = "Tu conductora está llegando en 4 min"),
        )
    }

    @Test
    fun `classifies food delivery in english and spanish`() {
        assertKind(LiveActivity.Kind.FOOD_ORDER, signals(text = "Your order is on the way - arriving in 12 min"))
        assertKind(LiveActivity.Kind.FOOD_ORDER, signals(text = "Tu pedido va en camino - llega en 12 min"))
    }

    @Test
    fun `classifies parcel delivery in english and spanish`() {
        assertKind(LiveActivity.Kind.PARCEL_DELIVERY, signals(text = "Package out for delivery - 2 stops away"))
        assertKind(LiveActivity.Kind.PARCEL_DELIVERY, signals(text = "Paquete en reparto - faltan 2 paradas"))
    }

    @Test
    fun `classifies immediate weather in english and spanish`() {
        assertKind(LiveActivity.Kind.WEATHER, signals(text = "Rain starts in 15 min"))
        assertKind(LiveActivity.Kind.WEATHER, signals(text = "La lluvia comienza en 15 min"))
    }

    @Test
    fun `classifies otp only with verification context`() {
        val english = classifier.classify(signals(text = "Your verification code is 482193"))
        val spanish = classifier.classify(signals(text = "Tu código de verificación es 482193"))

        assertEquals(LiveActivity.Kind.OTP, english?.kind)
        assertEquals(LiveActivity.Kind.OTP, spanish?.kind)
        assertTrue(english?.isSensitive == true)
        assertTrue(spanish?.isSensitive == true)
    }

    @Test
    fun `classifies structured countdown as timer`() {
        assertKind(LiveActivity.Kind.TIMER, signals(countdown = true))
    }

    @Test
    fun `structured progress handles determinate indeterminate complete and bounds`() {
        val determinate = classifier.classify(
            signals(progressCurrent = 42, progressMax = 100, hasProgressStyle = true),
        )
        val indeterminate = classifier.classify(
            signals(progressIndeterminate = true, hasProgressStyle = true),
        )
        val completed = classifier.classify(
            signals(progressCurrent = 100, progressMax = 100, hasProgressStyle = true),
        )
        val overMax = classifier.classify(
            signals(progressCurrent = 150, progressMax = 100, hasProgressStyle = true),
        )
        val belowZero = classifier.classify(
            signals(progressCurrent = -5, progressMax = 100, hasProgressStyle = true),
        )

        assertEquals(LiveActivity.Kind.GENERIC_PROGRESS, determinate?.kind)
        assertEquals(42, determinate?.progress?.current)
        assertEquals(LiveActivity.Kind.GENERIC_PROGRESS, indeterminate?.kind)
        assertTrue(indeterminate?.progress?.isIndeterminate == true)
        assertEquals(LiveActivity.Kind.GENERIC_PROGRESS, completed?.kind)
        assertEquals(100, completed?.progress?.current)
        assertEquals(100, overMax?.progress?.current)
        assertEquals(0, belowZero?.progress?.current)
    }

    @Test
    fun `text progress requires operation context`() {
        assertKind(LiveActivity.Kind.GENERIC_PROGRESS, signals(text = "Downloading update - 45% complete"))
        assertNull(classifier.classify(signals(text = "Humidity 80%")))
        assertNull(classifier.classify(signals(text = "Oferta del 50%")))
    }

    @Test
    fun `rejects competing semantic domains when scores tie`() {
        assertNull(
            classifier.classify(
                signals(text = "Order package on the way out for delivery"),
            ),
        )
    }

    @Test
    fun `rejects required false positives`() {
        val falsePositives = listOf(
            "20% de descuento",
            "Oferta del 50%",
            "2026 será increíble",
            "5512345678",
            "Humedad 80%",
            "Tu pedido #123456 fue recibido",
            "Código postal 03100",
            "Paquete de datos renovado",
            "Tu conductor favorito tiene una promoción",
            "Entrega gratis hoy",
            "Lluvia probable 30%",
            "La junta empieza en 5 min",
            "Nuevo mensaje: 482193",
            "Compra aprobada por $123456",
        )

        falsePositives.forEach { value ->
            assertNull("Unexpected live classification for: $value", classifier.classify(signals(text = value)))
        }
    }

    @Test
    fun `rejects weak single domain terms and empty content`() {
        assertNull(classifier.classify(signals(text = "driver")))
        assertNull(classifier.classify(signals(text = "delivery")))
        assertNull(classifier.classify(signals(text = "5 min")))
        assertNull(classifier.classify(signals()))
    }

    private fun assertKind(expected: LiveActivity.Kind, input: NotificationLiveSignals) {
        assertEquals(expected, classifier.classify(input)?.kind)
    }

    private fun signals(
        title: String? = null,
        text: String? = null,
        progressCurrent: Int? = null,
        progressMax: Int? = null,
        progressIndeterminate: Boolean = false,
        hasProgressStyle: Boolean = false,
        countdown: Boolean = false,
    ): NotificationLiveSignals = NotificationLiveSignals(
        packageName = "com.example.app",
        notificationKey = "0|com.example.app|7|null|1000",
        title = title,
        text = text,
        ongoing = true,
        clearable = false,
        postTime = 1_700_000_000_000L,
        progressCurrent = progressCurrent,
        progressMax = progressMax,
        progressIndeterminate = progressIndeterminate,
        hasProgressStyle = hasProgressStyle,
        countdown = countdown,
    )
}
