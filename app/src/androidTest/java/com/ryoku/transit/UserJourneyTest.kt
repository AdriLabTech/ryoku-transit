package com.ryoku.transit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UserJourneyTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun clean() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("favorites", 0).edit().clear().commit()
    }

    private fun waitForText(t: String) = rule.waitUntil(10_000) { rule.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun searchOpenStopFavoriteAndSeeFavorites() {
        waitForText("721 paradas")
        rule.onNodeWithContentDescription("Buscar parada por nombre o código").performTextInput("Brillante (A. Núñez)")
        waitForText("Parada 1")
        rule.onAllNodesWithText("Avda. Brillante (A. Núñez)")[0].performClick()
        waitForText("PRÓXIMAS SALIDAS")
        // La cobertura temporal debe ser visible: vigente o de referencia, nunca ausente.
        waitForText("Horario")
        rule.onNodeWithText("Sin posición ni predicción en vivo", substring = true).assertExists()
        rule.onNodeWithContentDescription("Favorita").performClick()
        rule.onNodeWithContentDescription("Favorita").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Favorita"))
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.onNodeWithContentDescription("Favoritas").performClick()
        waitForText("Avda. Brillante (A. Núñez)")
    }

    @Test fun statusScreenStatesLimitations() {
        waitForText("721 paradas")
        rule.onNodeWithText("ESTADO").performClick()
        waitForText("Capacidades".uppercase())
        rule.onAllNodesWithText("sin fuente accesible").assertCountEquals(3)
    }

    @Test fun mapOffersListAlternative() {
        waitForText("721 paradas")
        rule.onNodeWithContentDescription("Mapa").performClick()
        rule.onNodeWithText("Ver lista").assertHasClickAction().performClick()
        waitForText("Busca una parada")
    }
}
