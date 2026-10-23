package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class DrinkLibraryTest {

    @Test
    fun `drinks-library json parses all drinks and contains White Claw and BuzzBallz`() {
        val file = File("src/main/assets/drinks-library.json")
        val content = file.readText()
        val drinks = DrinkLibraryParser.parseJson(content)

        // Ensure parser parses the full collection
        val whiteClaw = drinks.find { it.name == "White Claw (All Flavours)" }
        assertNotNull("White Claw should exist in library", whiteClaw)
        assertEquals(330, whiteClaw!!.volumeMl)
        assertEquals(4.5, whiteClaw.abv, 0.001)
        assertEquals(95, whiteClaw.kcals)
        assertEquals(2.06, whiteClaw.cost, 0.001)
        assertEquals(1.5, whiteClaw.units, 0.001)

        val buzzBallzColada = drinks.find { it.name == "BuzzBallz (Fruit Ritas / Colada / Espresso)" }
        assertNotNull(buzzBallzColada)
        assertEquals(200, buzzBallzColada!!.volumeMl)
        assertEquals(13.5, buzzBallzColada.abv, 0.001)
        assertEquals(265, buzzBallzColada.kcals)
        assertEquals(4.0, buzzBallzColada.cost, 0.001)
        assertEquals(2.7, buzzBallzColada.units, 0.001)

        val buzzBallzMartini = drinks.find { it.name == "BuzzBallz (Pornstar Martini)" }
        assertNotNull(buzzBallzMartini)
        assertEquals(200, buzzBallzMartini!!.volumeMl)
        assertEquals(13.5, buzzBallzMartini.abv, 0.001)
        assertEquals(288, buzzBallzMartini.kcals)
        assertEquals(4.0, buzzBallzMartini.cost, 0.001)
        assertEquals(2.7, buzzBallzMartini.units, 0.001)

        val buzzBallzMango = drinks.find { it.name == "BuzzBallz (Chili Mango)" }
        assertNotNull(buzzBallzMango)
        assertEquals(200, buzzBallzMango!!.volumeMl)
        assertEquals(15.0, buzzBallzMango.abv, 0.001)
        assertEquals(252, buzzBallzMango.kcals)
        assertEquals(4.0, buzzBallzMango.cost, 0.001)
        assertEquals(3.0, buzzBallzMango.units, 0.001)
    }
}
