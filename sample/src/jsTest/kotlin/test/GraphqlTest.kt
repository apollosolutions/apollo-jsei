package test

import sample.GetAnimalLionAnimal
import sample.GetStuffData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphqlTest {

    @Test
    fun animalTest() {
        val o = js("{ __typename: \"Lion\", species: null, roar: \"Rooar\" }") as GetAnimalLionAnimal

        println("Animal: ${o.__typename} ${o.species}")

        assertEquals("Lion", o.__typename)
        o as Lion
        assertEquals("Rooar", o.roar)
        assertNull(o.species)
    }

    @Test
    fun enumTest() {
        val o = js("{ direction: \"NORTH\", date: null  }") as GetStuffData

        assertEquals("NORTH", o.direction)
        // it's possible to put null into a non-nullable field
        assertEquals<Any?>(null, o.date)
    }
}
