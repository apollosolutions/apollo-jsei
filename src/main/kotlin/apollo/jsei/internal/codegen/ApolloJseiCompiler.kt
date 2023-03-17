package apollo.jsei.internal.codegen

import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.ast.*
import com.apollographql.apollo3.ast.introspection.toSchema
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import okio.buffer
import okio.source
import java.io.File
import java.util.*

@OptIn(ApolloExperimental::class)
object ApolloJseiCompiler {
    fun compile(
        files: Collection<File>,
        outputDir: File,
        packageName: String
    ) {
        val operations = mutableListOf<GQLOperationDefinition>()
        val fragments = mutableListOf<GQLFragmentDefinition>()
        val schemaDefinitions = mutableListOf<GQLDefinition>()

        files.map { file ->
            file.toSchema().toGQLDocument()
        }.flatMap {
            it.definitions
        }.forEach {
            when (it) {
                is GQLOperationDefinition -> operations.add(it)
                is GQLFragmentDefinition -> fragments.add(it)
                else -> schemaDefinitions.add(it)
            }
        }

        check(schemaDefinitions.isNotEmpty()) {
            "ApolloJsei: no type definitions found: did you add a schema?"
        }

        val schema = GQLDocument(schemaDefinitions, null).validateAsSchema().valueAssertNoErrors()

        val fragmentsMap = fragments.associateBy { it.name }
        operations.forEach {
            it.validate(schema, fragments = fragmentsMap).checkNoErrors()
        }


        val codegenSchema = CodegenSchema(schema, schema.scalarMapping())

        outputDir.deleteRecursively()

        operations.map {
            it.toFileSpec(codegenSchema, fragmentsMap, packageName)
        }.forEach {
            it.writeTo(outputDir)
        }
    }
}

inline fun <reified T> Any?.safeCast() = this as? T

internal fun Schema.scalarMapping(): Map<String, String> {
    return buildMap {
        typeDefinitions.values.filterIsInstance<GQLScalarTypeDefinition>().forEach {
            var target = it.directives.firstOrNull { it.name == "mapsTo" }
                ?.arguments
                ?.arguments
                ?.single()
                ?.value
                ?.safeCast<GQLStringValue>()
                ?.value

            if (target == null) {
                target = when (it.name) {
                    "Int" -> "kotlin.Int"
                    "String" -> "kotlin.String"
                    "Float" -> "kotlin.Double"
                    "Boolean" -> "kotlin.Boolean"
                    "ID" -> "kotlin.String"
                    else -> "kotlin.Any"
                }
            }

            put(it.name, target)
        }
    }
}

internal class CodegenSchema(val schema: Schema, val scalarMapping: Map<String, String>)

private fun GQLOperationDefinition.toFileSpec(
    codegenSchema: CodegenSchema,
    fragments: Map<String, GQLFragmentDefinition>,
    packageName: String
): FileSpec {

    val filename = name?.capitalizeFirstLetter()
        ?: throw SourceAwareException("ApolloJsei: anonymous operations are not supported", sourceLocation)

    return TypeSpecsBuilder(
        prefix = filename,
        dataField = GQLField(
            sourceLocation = SourceLocation.UNKNOWN,
            alias = null,
            name = "data",
            arguments = null,
            directives = emptyList(),
            selectionSet = this@toFileSpec.selectionSet
        ),
        parentType = codegenSchema.schema.rootTypeNameFor(operationType),
        fragments = fragments,
        codegenSchema = codegenSchema,
        packageName = packageName
    ).build()
        .let {
            FileSpec.builder(packageName, filename)
                .apply {
                    addType(responseTypeSpec(packageName, filename))
                    it.forEach {
                        addType(it)
                    }
                }
                .build()
        }
}

private fun responseTypeSpec(packageName: String, name: String): TypeSpec {
    return TypeSpec.interfaceBuilder("${name}Response")
        .addModifiers(KModifier.EXTERNAL)
        .addProperty(PropertySpec.builder("data", ClassName(packageName, "${name}Data").copy(nullable = true)).build())
        .addProperty(PropertySpec.builder(
            "errors",
            ClassName("kotlin", "Array")
                .parameterizedBy(ClassName("kotlin", "Any"))
                .copy(nullable = true)
        ).build())
        .build()
}
internal class TypeSpecsBuilder(
    val prefix: String,
    val dataField: GQLField,
    val parentType: String,
    val codegenSchema: CodegenSchema,
    val fragments: Map<String, GQLFragmentDefinition>,
    val packageName: String
) {
    private val usedNames = mutableSetOf<String>()
    private val schema = codegenSchema.schema
    private val nameMap = mutableMapOf<JsPath, String>()

    private fun buildMap(field: JsField) {
        field.fieldSets.forEach {
            buildMap(it)
        }
    }

    private fun buildMap(fieldSet: JsFieldSet) {
        var name = if (fieldSet.implements != null) {
            fieldSet.path.last().let {
                "${it.typename.capitalizeFirstLetter()}${it.name.capitalizeFirstLetter()}"
            }
        } else {
            fieldSet.path.last().name.capitalizeFirstLetter()
        }

        var index = 1
        while (usedNames.contains(name)) {
            name = "${name}$index"
            index++
        }
        usedNames.add(name)
        nameMap.put(fieldSet.path, "${prefix}$name")

        fieldSet.fields.forEach {
            buildMap(it)
        }
    }

    fun build(): List<TypeSpec> {

        val field = buildField(emptyList(), dataField, GQLNonNullType(type = GQLNamedType(name = parentType)), false)

        buildMap(field)

        return field.toTypeSpecs()
    }

    private fun JsField.toTypeSpecs(): List<TypeSpec> {
        return fieldSets.flatMap { it.toTypeSpecs() }
    }

    private fun IrType.toTypename(): TypeName {
        return when (this) {
            is IrNullableType -> ofType.toTypename().copy(nullable = true)
            is IrListType -> ClassName("kotlin", "Array").parameterizedBy(ofType.toTypename())
            is IrModelType -> ClassName(packageName, nameMap[path]!!)
            is IrNamedType -> {
                when (name) {
                    "Int" -> ClassName("kotlin", "Int")
                    "String" -> ClassName("kotlin", "String")
                    "ID" -> ClassName("kotlin", "String")
                    "Float" -> ClassName("kotlin", "Double")
                    "Boolean" -> ClassName("kotlin", "Boolean")
                    else -> {
                        val typeDefinition = schema.typeDefinition(name)

                        if (typeDefinition is GQLEnumTypeDefinition) {
                            ClassName("kotlin", "String")
                        } else {
                            ClassName("kotlin", "Any")
                        }
                    }
                }
            }
        }
    }

    private fun JsFieldSet.toTypeSpecs(): List<TypeSpec> {
        val builder = TypeSpec.interfaceBuilder(nameMap[path]!!)
            .addModifiers(KModifier.EXTERNAL)

        implements?.also {
            builder.addSuperinterface(ClassName(packageName = packageName, nameMap[it]!!))
        }

        fields.forEach {
            builder.addProperty(
                PropertySpec.builder(it.responseName, it.irType.toTypename())
                    .apply {
                        if (it.override) {
                            addModifiers(KModifier.OVERRIDE)
                        }
                    }
                    .build()
            )
        }

        return listOf(builder.build()) + fields.flatMap { it.toTypeSpecs() }
    }

    private fun GQLType.toIrInternal(pathContext: JsPath?): IrType {
        return when (this) {
            is GQLListType -> IrListType(type.toIr(pathContext))
            is GQLNamedType -> if (pathContext != null) {
                IrModelType(pathContext)
            } else {
                IrNamedType(name)
            }

            is GQLNonNullType -> error("Badly wrapped non-null")
        }
    }

    private fun GQLType.toIr(pathContext: JsPath?): IrType {
        return if (this is GQLNonNullType) {
            type.toIrInternal(pathContext)
        } else {
            IrNullableType(this.toIrInternal(pathContext))
        }
    }

    private fun buildFieldSet(
        path: List<JsPathElement>,
        field: GQLField,
        parentType: String,
        superFieldSet: JsFieldSet?
    ): JsFieldSet {
        val selfPath = path + JsPathElement(field.responseName(), parentType)
        val allFields = collectFields(field.selectionSet?.selections.orEmpty(), parentType)
            .groupBy {
                it.responseName()
            }.map {
                val first = it.value.first()
                val selections = it.value.flatMap { it.selectionSet?.selections.orEmpty() }

                val override = superFieldSet?.fields?.any {
                    it.responseName == first.responseName()
                } ?: false

                buildField(
                    path = selfPath,
                    field = first.copy(selectionSet = first.selectionSet?.copy(selections = selections)),
                    type = first.definitionFromScope(schema, parentType)!!.type,
                    override = override
                )
            }

        return JsFieldSet(selfPath, allFields, superFieldSet?.path)
    }

    private fun collectFields(selections: List<GQLSelection>, parentType: String): List<GQLField> {
        return selections.flatMap {
            when (it) {
                is GQLField -> listOf(it)
                is GQLInlineFragment -> {
                    if (schema.isTypeASuperTypeOf(it.typeCondition.name, parentType)) {
                        collectFields(it.selectionSet.selections, parentType)
                    } else {
                        emptyList()
                    }
                }

                is GQLFragmentSpread -> {
                    val fragment = fragments.get(it.name) ?: error("Cannot find fragment ${it.name}")
                    if (schema.isTypeASuperTypeOf(fragment.typeCondition.name, parentType)) {
                        collectFields(fragment.selectionSet.selections, parentType)
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }

    private fun buildField(path: JsPath, field: GQLField, type: GQLType, override: Boolean): JsField {

        val parentType = type.rawType().name
        if (field.selectionSet?.selections.orEmpty().isEmpty()) {
            return JsField(
                responseName = field.responseName(),
                irType = type.toIr(null),
                fieldSets = emptyList(),
                override = override
            )
        }

        val possibleTypes = schema.possibleTypes(parentType)

        val baseFieldSet = buildFieldSet(path, field, parentType, null)

        val otherFieldSets = if (possibleTypes.size > 1) {
            possibleTypes.map {
                buildFieldSet(path, field, it, baseFieldSet)
            }
        } else {
            emptyList()
        }

        return JsField(
            responseName = field.responseName(),
            irType = type.toIr(baseFieldSet.path),
            fieldSets = listOf(baseFieldSet) + otherFieldSets,
            override = override
        )
    }

}

internal fun String.capitalizeFirstLetter(): String {
    return replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(
            Locale.US
        ) else it.toString()
    }
}

internal class JsField(
    val responseName: String,
    val irType: IrType,
    val override: Boolean,
    val fieldSets: List<JsFieldSet>
)

internal class JsFieldSet(
    val path: JsPath,
    val fields: List<JsField>,
    val implements: JsPath?,
) {
}

typealias JsPath = List<JsPathElement>