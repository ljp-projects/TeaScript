package runtime.types

import frontend.*
import runtime.CompilationEnvironment
import runtime.Environment
import runtime.eval.evalObjectExpr

/**
 * Used as a base for all native TeaScript types to inherit from.
 */
interface Type {
    infix fun matches(v: RuntimeVal): Boolean
}

/**
 * Used as a base for all native TeaScript types to inherit from.
 */
interface NullableType : Type {
    override infix fun matches(v: RuntimeVal): Boolean
}

class AnyType : Type {
    override fun matches(v: RuntimeVal): Boolean = true

    override fun toString(): String = "any"
}

class NeverType : Type {
    override fun matches(v: RuntimeVal): Boolean = false

    override fun toString(): String = "never"
}

class FunctionType : Type {
    override fun matches(v: RuntimeVal): Boolean = v is FunctionValue

    override fun toString(): String = "func"
}

class NumberType : Type {
    override fun matches(v: RuntimeVal): Boolean = v is NumberVal

    override fun toString(): String = "number"
}

class StringType : Type {
    override fun matches(v: RuntimeVal): Boolean = v is StringVal

    override fun toString(): String = "str"
}

data class NullableTypeWrapper(val baseType: Type) : NullableType {
    override fun matches(v: RuntimeVal): Boolean = baseType matches v || v is NullVal

}

data class UnionType(val first: Type, val second: Type) : Type {
    override fun matches(v: RuntimeVal): Boolean = first matches v || second matches v

}

data class CombinedType(val types: HashSet<Type>) : Type {
    constructor(vararg types: Type) : this(hashSetOf(*types))

    override fun matches(v: RuntimeVal): Boolean = types.any { it matches v }

}

val nativeTypes = hashSetOf(
    "str",
    "number",
    "object",
    "func",
    "bool"
)

fun typeEval(t: TypeExpr, env: Environment): Type = when (t) {
    is Identifier -> object : Type {
        override fun matches(v: RuntimeVal): Boolean = (v.kind == t.symbol) || t.symbol == "any"
        override fun toString(): String = t.symbol
    }

    is StringLiteral -> object : Type {
        override fun matches(v: RuntimeVal): Boolean = v.value == t.value
        override fun toString(): String = t.value
    }

    is NumberLiteral -> object : Type {
        override fun matches(v: RuntimeVal): Boolean = v.value == t.value
        override fun toString(): String = t.value.toString()
    }

    is ObjectLiteral -> object : Type {
        override fun matches(v: RuntimeVal): Boolean {
            val pairedProps = evalObjectExpr(t, env)

            return pairedProps == v.value
        }
    }

    else -> AnyType()
}

interface Typed {
    val type: Type
}

fun typeEval(t: TypeExpr, env: CompilationEnvironment): Type = when (t) {
    is Identifier -> object : Type {
        override fun matches(v: RuntimeVal): Boolean = v.kind == t.symbol
        override fun toString(): String = t.symbol
    }

    is StringLiteral -> object : Type {
        override fun matches(v: RuntimeVal): Boolean = v.value == t.value
        override fun toString(): String = t.value
    }

    is NumberLiteral -> object : Type {
        override fun matches(v: RuntimeVal): Boolean = v.value == t.value
        override fun toString(): String = t.value.toString()
    }

    else -> AnyType()
}