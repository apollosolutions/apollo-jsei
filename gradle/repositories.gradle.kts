listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach {
    it.apply {
        mavenCentral()
        mavenLocal()
//        maven {
//            url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//        }
    }
}
