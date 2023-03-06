rootProject.name = "sample"

pluginManagement {
    includeBuild("../")
}
apply(from = "../gradle/repositories.gradle.kts")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}