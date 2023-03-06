plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.apollographql.jsei")
}

kotlin {
    js(IR) {
        nodejs {
            testTask {
                useMocha()
            }
        }
    }

    sourceSets {
        val jsTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

apolloJsei {
    service("service") {
        graphqlFiles.from(fileTree("src/jsMain/graphql"))
        packageName.set("sample")
    }
}