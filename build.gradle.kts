@file:Suppress("UnstableApiUsage")

import kotlinx.coroutines.runBlocking
import net.mbonnin.vespene.lib.NexusStagingClient

buildscript {
    dependencies {
        classpath(libs.vespene)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)

}
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile::class.java).configureEach {
    kotlinOptions.jvmTarget = "11"
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.gr8)
    id("java-gradle-plugin")
    id("maven-publish")
}

dependencies {
    compileOnly(libs.gradle.api)

    implementation(libs.kotlinpoet)
    implementation(libs.kgp.min)
    implementation(libs.apollo.ast)
}

gr8 {
    removeGradleApiFromApi()
}

group = "com.apollographql.jsei"
version = "0.0.1-SNAPSHOT"

gradlePlugin {
    this.plugins {
        this.create("com.apollographql.jsei") {
            this.id = "com.apollographql.jsei"
            this.implementationClass = "apollo.jsei.api.ApolloJseiPlugin"
        }
    }
}

fun Project.getOssStagingUrl(): String {
    val url = try {
        this.extensions.extraProperties["ossStagingUrl"] as String?
    } catch (e: ExtraPropertiesExtension.UnknownPropertyException) {
        null
    }
    if (url != null) {
        return url
    }
    val baseUrl = "https://s01.oss.sonatype.org/service/local/"
    val client = NexusStagingClient(
        baseUrl = baseUrl,
        username = System.getenv("SONATYPE_NEXUS_USERNAME"),
        password = System.getenv("SONATYPE_NEXUS_PASSWORD"),
    )
    val repositoryId = runBlocking {
        client.createRepository(
            profileId = System.getenv("COM_APOLLOGRAPHQL_PROFILE_ID"),
            description = "apollo-kotlin $version"
        )
    }
    return "${baseUrl}staging/deployByRepositoryId/${repositoryId}/".also {
        this.extensions.extraProperties["ossStagingUrl"] = it
    }
}

publishing {
    this.repositories {
        maven {
            name = "ossSnapshots"
            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            credentials {
                username = System.getenv("SONATYPE_NEXUS_USERNAME")
                password = System.getenv("SONATYPE_NEXUS_PASSWORD")
            }
        }

        maven {
            name = "ossStaging"
            setUrl {
                uri(rootProject.getOssStagingUrl())
            }
            credentials {
                username = System.getenv("SONATYPE_NEXUS_USERNAME")
                password = System.getenv("SONATYPE_NEXUS_PASSWORD")
            }
        }
    }
}