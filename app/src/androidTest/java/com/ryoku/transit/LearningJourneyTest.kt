package com.ryoku.transit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.ryoku.transit.data.ObservationStore
import com.ryoku.transit.domain.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Los datos de aprendizaje de estas pruebas son SINTÉTICOS y se borran al terminar. */
class LearningJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    @Before fun clean() { ObservationStore(ctx).clear(); rule.runOnUiThread { vm.reloadLearning() } }
    @After fun cleanup() { ObservationStore(ctx).clear(); rule.runOnUiThread { vm.reloadLearning() } }

    private fun waitText(t: String, sub: Boolean = true) = rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = sub).fetchSemanticsNodes().isNotEmpty() }
    private fun openStop1() {
        waitText("721 paradas")
        rule.onNodeWithContentDescription("Buscar parada por nombre o código").performTextInput("Brillante (A. Núñez)")
        waitText("Parada 1")
        rule.onAllNodesWithText("Avda. Brillante (A. Núñez)")[0].performClick()
        waitText("PRÓXIMAS SALIDAS")
    }

    @Test fun manualFeedbackIsRecordedAndShown() {
        openStop1()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("toca para anotar", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasContentDescription("toca para anotar", substring = true))[0].performClick()
        rule.onNodeWithText("Ahora").performClick()
        waitText("✓ anotado")
        assert(ObservationStore(ctx).all().size == 1)
        assert(ObservationStore(ctx).all()[0].source == ObservationSource.MANUAL)
    }

    @Test fun learnedAdjustmentAppearsOnlyAfterValidatedByBacktest() {
        waitText("721 paradas")
        // Toma una llegada real para construir observaciones coherentes (mismo viaje/línea/base) con +3 min sistemáticos.
        val first = (vm.arrivals("1") as ArrivalCalculator.Result.Ok).arrivals.first()
        val store = ObservationStore(ctx)
        val rnd = java.util.Random(3)
        repeat(20) { i ->
            store.save(Observation(first.routeId, first.direction, "1", "synthetic-$i", first.serviceDate, first.scheduledSec, first.scheduledSec + 180 + rnd.nextInt(31) - 15, first.basis, ObservationSource.MANUAL, 1_000L + i))
        }
        rule.runOnUiThread { vm.reloadLearning() }
        rule.waitUntil(10_000) { vm.evaluations[first.basis]?.enabled == true }
        openStop1()
        waitText("AJUSTADO")
    }

    @Test fun learningSectionExplainsTheRules() {
        waitText("721 paradas")
        rule.onNodeWithText("ESTADO").performClick()
        rule.onNodeWithText("APRENDIZAJE", substring = true).assertExists()
        rule.onNodeWithText("Aplicar ajuste").assertExists()
    }
}
