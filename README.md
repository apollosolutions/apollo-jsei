# Apollo JSEI (JavaScript External Interfaces) Gradle Plugin

**The code in this repository is experimental and has been provided for reference purposes only. Community feedback is welcome but this project may not be supported in the same way that repositories in the official [Apollo GraphQL GitHub organization](https://github.com/apollographql) are. If you need help you can file an issue on this repository, [contact Apollo](https://www.apollographql.com/contact-sales) to talk to an expert, or create a ticket directly in Apollo Studio.**

## About

Apollo JSEI creates Kotlin [external interfaces](https://kotlinlang.org/docs/js-interop.html#external-interfaces) for your javascript objects that match the shape of your GraphQL queries. You can read [this issue](https://github.com/apollographql/apollo-kotlin/issues/4728) for more context about the reasons for this plugin.

It reuses some internal parts of [Apollo Kotlin](https://github.com/apollographql/apollo-kotlin) but provides a much more lightweight (and much less type safe) way to model your GraphQL data in Kotlin.

## Installation

This project is not published. You can either build locally (`./gradlew publishToMavenLocal` or use as an included build) or use the SNAPSHOTS:

```
// build.gradle.kts
repositories {
  maven {
    url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
  }
  // other repositories...
}

```
## Sample project

Open the `sample` directory to see and example of the plugin in action with unit tests in `jsTest`

## Usage

Add the Gradle plugins to your `build.gradle[.kts]` script:

```kotlin
plugins {
    id("com.apollographql.jsei").version(latestVersion)
}
```

And configure the plugin:

```kotlin
apolloJsei {
    apolloJsei {
        service("service") {
            graphqlFiles.from(fileTree("src/jsMain/graphql"))
            packageName.set("sample")
        }
    }
}
```

Download your schema to `src/jsMain/graphql/schema.graphqls` and write a query in `src/jsMain/graphql/query.grapqhl`:

```graphql
query MyQuery {
    cat {
        id
        name
        meow
    }
}
```

Run codegen:

```
./gradlew generateServiceJsExternalInterfaces
```

The plugin generates Kotlin files under `build/generated/sources/apolloJsei` and links them automatically to your `jsMain` source set:

```kotlin
external interface MyQueryCat {
    val id: String
    val name: String?
    val meow: String
}
```

If you have a JS object, you can cast it to the expected external interface:

```kotlin
val jsObject = js("{ id: \"42\", name: \"Minette\", meow: null }") 
// cast here. This always succeeds because external interfaces are erased at runtime
jsObject as MyQueryCat
// You can have autocomplete here
println(jsObject.id) // "42"
println(jsObject.name) // "Minette"
// But this is not 100% typesafe
println(jsObject.meow) // null (even if meow was a String and not a String?)
```

**Polymorphism**

Polymorphism is supported. In this case, you will have to add `__typename` to your queries and do type checks:

```graphql
query GetAnimal {
    animal {
        __typename
        species
        ... on Cat {
            mustaches
        }
        ...lionFragment
    }
}
```

```kotlin
val jsObject = js("{ __typename: \"Lion\", species: null, roar: \"Rooar\" }") as GetAnimalLionAnimal

if (jsObject.__typename == "Lion") {
    // Cast 
    jsObject as Lion
    assertNull(jsObject.species)
    assertEquals("Rooar", jsObject.roar)
}
```

## Known Limitations

- Enums are mapped to `String`
- Custom scalars are always mapped to `Any`
- `__typename` 
- This is obviously not typesafe as nothing prevents the returned JSON to be of a non-compliant shape. If that happens, you will get an exception while accessing one of the fields.

For a more typesafe approach, see [Apollo Kotlin](https://github.com/apollographql/apollo-kotlin)

