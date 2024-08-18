package org.ljpprojects.teascript.frontend

import java.math.BigDecimal
import java.util.*
import kotlin.collections.HashSet

/**
 * Statements don't result in runtime values.
 */
interface Statement {
    val kind: String
}

/**
 * Expressions result in  runtime values, unlike statements.
 */
interface Expr : Statement

/**
 * Type expressions are used when doing type magic, and are a smaller subset of expressions.
 */
interface TypeExpr : Expr

/**
 * Typed expressions are expressions that have a type
 */
interface TypedExpr : Expr {
    val type: TypeExpr?
}

abstract class Program(
    final override val kind: String,
    val body: MutableList<Statement>
) : Statement {
    init {
        require(this.kind == "program") { "Key can't be ${this.kind}." }
    }
}

/**
 * A statement representing a variable declaration. Kind must be var-decl.
 */
abstract class VarDecl(
    final override val kind: String,
    val constant: Boolean,
    val identifier: Identifier,
    val modifiers: HashSet<Modifier>,
    val value: Expr?
) : Statement {
    init {
        require(this.kind == "var-decl") { "Key can't be ${this.kind}." }
    }
}

data class Parameter(
    override val kind: String = "param",
    val name: String,
    val index: Byte,
    override val type: TypeExpr?
): TypedExpr

open class Block(
    val kind: String = "block",
    val body: List<Statement>
) {
    operator fun iterator(): Iterator<Statement> = body.iterator()
}

open class ParameterBlock(
    body: List<Statement>,
    val parameters: HashSet<Parameter>,
    val typeParams: HashSet<Parameter>
): Block(body = body)

/**
 * An expression representing a function declaration. Kind must be fn-decl.
 */
open class FunctionDecl(
    final override val kind: String,
    val name: Identifier?,
    val block: ParameterBlock,
    val modifiers: HashSet<Modifier>,
) : Expr {
    init {
        require(this.kind == "fn-decl") { "Key can't be ${this.kind}." }
    }

    fun hasModifier(type: ModifierType, value: String = "YES") =
        modifiers.any { it.type == type && it.value == value }
}

/**
 * A statement representing a for loop declaration. Kind must be for-decl.
 */
abstract class ForDecl(
    final override val kind: String,
    val parameter: Identifier,
    val obj: Expr,
    val body: Block,
    val modifiers: Set<Modifier>,
) : Statement {
    init {
        require(this.kind == "for-decl") { "Key can't be ${this.kind}." }
    }
}

/**
 * A statement representing a use declaration. Kind must be use-decl.
 */
abstract class ImportDecl(
    final override val kind: String,
    val file: String,
    val symbols: HashSet<String>,
    val net: Boolean
) : Statement {
    init {
        require(this.kind == "use-decl") { "Key can't be ${this.kind}." }
    }
}

open class ArrayLiteral(
    val parameter: Identifier,
    val elements: List<Expr>,
    val modifiers: Set<Modifier>,
    final override val kind: String = "array-lit",
): TypeExpr {
    init {
        require(this.kind == "array-lit") { "Key can't be ${this.kind}." }
    }
}

/*
type MyType {
    constant myValue: number

    func add(a: number, b: number) -> number
}
*/

abstract class ReturnStatement(
    val value: Expr,
    override val kind: String = "return"
): Statement

abstract class LockedBlock(
    val items: HashSet<String>,
    val body: Block,
    final override val kind: String = "locked-block"
): Expr

abstract class TypeDecl(
    val def: AbstrDeclBlock,
    val name: Identifier?,
    final override val kind: String = "type-decl"
): TypeExpr

/**
 * An expression representing an await block declaration. Kind must be await-decl.
 */
abstract class AwaitDecl(
    final override val kind: String,
    val parameter: Identifier,
    val obj: Expr,
    val body: Block,
    val modifiers: Set<Modifier>
) : Expr {
    init {
        require(this.kind == "await-decl") { "Key can't be ${this.kind}." }
    }
}

/**
 * An expression representing an after block declaration. Kind must be after-decl.
 */
open class AfterDecl(
    final override val kind: String,
    val ms: Expr,
    val body: Block,
    val modifiers: Set<Modifier>,
) : Expr {
    init {
        require(this.kind == "after-decl") { "Key can't be ${this.kind}." }
    }

    val async: Boolean
        get() = this.modifiers.none { it.type == ModifierType.Synchronised }
}

/**
 * An expression representing an if block declaration. Kind must be if-decl.
 */
abstract class IfDecl(
    final override val kind: String,
    val cond: Expr,
    val body: Block,
    val modifiers: Set<Modifier>,
    val otherwise: Block?,
    val or: ArrayDeque<OrDecl>,
) : Expr {
    init {
        require(this.kind == "if-decl") { "Key can't be ${this.kind}." }
    }
}

/**
 * A statement representing an or block declaration, which is used after an if. Kind must be or-decl.
 */
abstract class OrDecl(
    final override val kind: String,
    val cond: Expr,
    val body: Block,
) : Expr {
    init {
        require(this.kind == "or-decl") { "Key can't be ${this.kind}." }
    }
}

/**
 * An expression representing an assignment to a variable. Kind must be assign-expr.
 */
abstract class AssignmentExpr(
    final override val kind: String,
    val assigned: Expr,
    val value: Expr,
) : Expr {
    init {
        require(this.kind == "assign-expr") { "Key can't be ${this.kind}." }
    }
}

/**
 * An expression representing a binary operation of another expression. Kind must be binary-expr.
 */
abstract class BinaryExpr(
    final override val kind: String,
    val left: Expr,
    val right: Expr,
    val operator: String
) : Expr {
    init {
        require(this.kind == "binary-expr") { "Key can't be ${this.kind}." }
    }
}

/**
 * An expression representing a unary operation of another expression. Kind must be unary-expr.
 */
abstract class UnaryExpr(
    final override val kind: String = "unary-expr",
    val expr: Expr,
    val operator: String
) : Expr {
    init {
        require(this.kind == "unary-expr") { "Key can't be ${this.kind}." }
    }
}

/**
 * An expression representing a call to a function. Kind must be call-expr.
 */
open class CallExpr(
    final override val kind: String,
    val args: List<Expr>,
    val typeParams: List<TypeExpr>,
    val caller: Expr,
    private val modifiers: HashSet<Modifier>
) : Expr {
    init {
        require(this.kind == "call-expr") { "Key can't be ${this.kind}." }
    }

    fun hasModifier(type: ModifierType, value: String) =
        modifiers.any { it.type == type && it.value == value }
}

/**
 * An expression representing access to an object's member. Kind must be member-expr.
 */
abstract class MemberExpr(
    final override val kind: String,
    val obj: Expr,
    val prop: Expr,
    val computed: Boolean,
) : TypeExpr {
    init {
        require(this.kind == "member-expr") { "Key can't be ${this.kind}." }
    }
}

/**
 * A value representing an identifier. Kind must be ident.
 */
open class Identifier(
    final override val kind: String = "ident",
    val symbol: String,
    var type: TypeExpr?,
    val typeParams: List<TypeExpr> = listOf()
) : Expr {
    init {
        require(this.kind == "ident") { "Key can't be ${this.kind}." }
    }
}

/**
 * A value representing a numeral. Kind must be num-lit.
 */
abstract class NumberLiteral(
    final override val kind: String,
    val value: BigDecimal,
) : TypeExpr {
    init {
        require(this.kind == "num-lit") { "Key can't be ${this.kind}." }
    }
}

/**
 * A value representing a string. Kind must be str-lit.
 */
abstract class StringLiteral(
    final override val kind: String,
    val value: String,
) : TypeExpr {
    init {
        require(this.kind == "str-lit") { "Key can't be ${this.kind}." }
    }
}

/**
 * A value representing a property of an object. Kind must be "prop".
 */
abstract class Property(
    final override val kind: String,
    val key: String,
    val value: Expr?,
    val type: TypeExpr?,
    val constant: Boolean,
) : Expr {
    init {
        require(this.kind == "prop") { "Key can't be ${this.kind}." }
    }
}

fun makeProperty(key: String, value: Expr?, type: TypeExpr?, constant: Boolean) =
    object : Property("prop", key, value, type, constant) {}

/**
 * A value representing an object. Kind must be obj-lit.
 */
abstract class ObjectLiteral(
    final override val kind: String,
    val properties: List<Property>,
    val conformsTo: HashSet<Identifier>,
) : TypeExpr {
    init {
        require(this.kind == "obj-lit") { "Key can't be ${this.kind}." }
    }
}

open class NullableType(
    override val kind: String = "nullable-type",
    val base: TypeExpr,
): TypeExpr {
    override fun toString(): String {
        return "?$base"
    }
}

open class IdentType(
    override val kind: String = "ident-type",
    val name: String,
    open val typeParams: HashSet<Pair<TypeExpr, Byte>>
): TypeExpr {
    override fun toString(): String {
        return "$name${if (typeParams.size > 0) "<" else ""}${typeParams.sortedBy { it.second }.joinToString(", ") { it.first.toString() }}${if (typeParams.size > 0) ">" else ""}"
    }
}

open class EnumEntryType(
    final override val kind: String = "enum-entry-type",
    name: String,
    val entryName: String,
    typeParams: java.util.HashSet<Pair<TypeExpr, Byte>>
): IdentType(kind, name, typeParams) {
    override fun toString(): String {
        return "$name${if (typeParams.size > 0) "<" else ""}${typeParams.sortedBy { it.second }.joinToString(", ") { it.first.toString() }}${if (typeParams.size > 0) ">" else ""}::$entryName"
    }
}

open class FuncType(
    final override val kind: String = "func-type",
    val argTypes: HashSet<Pair<TypeExpr, Byte>>,
    val returnType: TypeExpr,
    val typeParams: HashSet<Pair<IdentType, Byte>>
): TypeExpr {
    override fun toString(): String {
        return "${if (typeParams.size > 0) "<" else ""}${typeParams.sortedBy { it.second }.joinToString(", ") { it.first.toString() }}${if (typeParams.size > 0) ">" else ""} (${argTypes.sortedBy { it.second }.joinToString(", ") { it.first.toString() }}) -> $returnType"
    }
}