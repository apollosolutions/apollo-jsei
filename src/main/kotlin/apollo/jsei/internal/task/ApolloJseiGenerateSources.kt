package apollo.jsei.internal.task;

import apollo.jsei.internal.codegen.ApolloJseiCompiler
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

@CacheableTask
public abstract class ApolloJseiGenerateSources: DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val graphqlFiles: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun taskAction() {
        val packageName = packageName.orNull ?: error("ApolloJsei: packageName missing")
        ApolloJseiCompiler.compile(graphqlFiles.files, outputDir.get().asFile, packageName)
    }
}
