package apollo.jsei.api

import apollo.jsei.internal.codegen.capitalizeFirstLetter
import apollo.jsei.internal.task.ApolloJseiGenerateSources
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.*

class ApolloJseiPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.add("apolloJsei", JseiBuilder(project = target))
    }
}

@DslMarker
annotation class JseiDsl

class JseiServiceBuilder internal constructor(private val project: Project) {
    val packageName: Property<String> = project.objects.property(String::class.java)
    val graphqlFiles: ConfigurableFileCollection = project.files()
}

@JseiDsl
class JseiBuilder internal constructor(private val project: Project) {
    private val services = mutableMapOf<String, JseiServiceBuilder>()

    fun service(name: String, block: Action<JseiServiceBuilder>) {
        check(!services.containsKey(name)) {
            "ApolloJsei: there is already a service named '$name'"
        }
        val serviceBuilder = JseiServiceBuilder(project)

        block.execute(serviceBuilder)

        services.put(name, serviceBuilder)

        val taskProvider = project.tasks.register("generate${name.capitalizeFirstLetter()}JsExternalInterfaces", ApolloJseiGenerateSources::class.java) {
            it.graphqlFiles.from(serviceBuilder.graphqlFiles)
            it.packageName.set(serviceBuilder.packageName)
            it.outputDir.set(project.layout.buildDirectory.dir("generated/sources/apolloJsei"))
        }

        val kotlinExtension = project.extensions.getByName("kotlin") as? KotlinMultiplatformExtension

        check (kotlinExtension != null) {
            "ApolloJsei: org.jetbrains.kotlin.multiplatform plugin not found"
        }

        val jsMain = kotlinExtension.sourceSets.findByName("jsMain")

        check(jsMain != null) {
            "ApolloJsei: no jsMain source set found"
        }

        jsMain.kotlin.srcDir(taskProvider.flatMap { it.outputDir })
    }
}

