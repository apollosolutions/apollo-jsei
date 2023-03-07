package apollo.jsei.internal.codegen

sealed interface IrType

data class JsPathElement(val name: String, val typename: String)

class IrNamedType(val name: String): IrType
class IrModelType(val path: List<JsPathElement>): IrType
class IrNullableType(val ofType: IrType): IrType
class IrListType(val ofType: IrType): IrType

