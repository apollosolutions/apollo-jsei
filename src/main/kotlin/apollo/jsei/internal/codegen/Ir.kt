package apollo.jsei.internal.codegen

class IrProperty(
    val name: String,
    val type: IrType
)

sealed interface IrType

data class JsPathElement(val name: String, val typename: String)

class IrNamedType(val name: String): IrType
class IrModelType(val path: List<JsPathElement>): IrType
class IrNullableType(val ofType: IrType): IrType
class IrListType(val ofType: IrType): IrType

class IrModel(
    val path: List<JsPathElement>,
    val properties: List<IrProperty>
)