package test

import kotlin.test.Test
import kotlin.test.assertFails

class SimpleTest {
    @Test
    fun test() {
        val o = js("{ hello: \"world\"}") as Hello

        println("Hello: ${o.hello} (${o.hello.length} chars)")
        println("Hello: ${o.other}")
    }

    @Test
    fun animalTest() {
        val o = js("{ __typename: \"Lion\", species: \"Feline\", roar: \"Rooar\" }") as Animal

        println("Animal: ${o.__typename} ${o.species}")

        when(o.__typename) {
            "Lion" -> println((o as Lion).roar)
        }
    }

    @Test
    fun enumTest() {
        val o = js("{ direction: \"NORTH\" }") as Path

        // Doesn't work because we get strings, not enums
        assertFails {
            when (o.direction) {
                Direction.NORTH -> println("Going north")
                Direction.SOUTH -> println("Going south")
            }
        }
    }
}

external interface Hello {
    val hello: String
    val other: String
}

external interface Animal {
    val __typename: String
    val species: String
}

external interface Lion: Animal {
    val roar: String
}


enum class Direction {
    NORTH,
    SOUTH
}
external interface Path {
    val direction: Direction
}