package org.ljpprojects.teascript.runtime.types

import org.ljpprojects.teascript.frontend.*
import org.ljpprojects.teascript.runtime.Environment
import java.math.BigInteger

/**
 * Used as a base for all native TeaScript types to inherit from.
 */
open class Type {
    open val superTypes: HashSet<out Type> = hashSetOf()
    open val subTypes: HashSet<out Type> = hashSetOf()

    open infix fun matches(v: RuntimeVal): Boolean = false
    open infix fun matchesType(t: Type): Boolean = false

    override fun equals(other: Any?): Boolean {
        if (other !is Type) return false
        if (other.subTypes != subTypes) return false
        if (other.superTypes != superTypes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = superTypes.hashCode()
        result = 31 * result + subTypes.hashCode()
        return result
    }
}

class AnyType: Type() {
    override fun matches(v: RuntimeVal): Boolean = true
    override fun matchesType(t: Type): Boolean = true
    override fun toString(): String = "any"
}

class NeverType: Type() {
    override fun toString(): String = "never"
}

open class NumberType: Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())
    override val subTypes: HashSet<out Type> = hashSetOf()

    override fun matches(v: RuntimeVal): Boolean = v is NumberVal
    override fun toString(): String = "number"
}

class IntegerType: NumberType() {
    override val superTypes: HashSet<out Type> = hashSetOf(NumberType())
    override val subTypes: HashSet<out Type> = hashSetOf()

    override fun matches(v: RuntimeVal): Boolean = v is NumberVal && try { v.value.toBigIntegerExact() is BigInteger } catch (e: ArithmeticException) { false }
    override fun toString(): String = "integer"
}

class StringType: Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())
    override val subTypes: HashSet<out Type> = hashSetOf()

    override fun matches(v: RuntimeVal): Boolean = v is StringVal || v.kind == toString()
    override fun toString(): String = "str"
}

class NullType: Type() {
    override fun matches(v: RuntimeVal): Boolean = v is NullVal || v.kind == "null"

    override fun toString(): String = "null"
}

class BoolType: Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())
    override val subTypes: HashSet<out Type> = hashSetOf()

    override fun matches(v: RuntimeVal): Boolean = v is BoolVal
    override fun toString(): String = "bool"
}

class PromiseType: Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())
    override val subTypes: HashSet<out Type> = hashSetOf()

    override fun matches(v: RuntimeVal): Boolean = v is PromiseVal
    override fun toString(): String = "promise"
}

open class AnyObjectType: Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())

    override fun matches(v: RuntimeVal): Boolean = v is ObjectVal
    override fun toString(): String = "object"
}

open class ObjectType(private val fields: HashMap<String, Type>, val acceptOthersWithTypeIn: HashSet<Type> = hashSetOf()): Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())

    override fun matches(v: RuntimeVal): Boolean {
        if (v !is ObjectVal) return false

        val typesMatch = !v.value.any { (name, value) ->
            val type = value.second

            if (fields[name] == null) return@any true
            if (!checkTypeRecursive(fields[name]!!, type)) {
                println("${fields[name]} ($name) has not matched type $type.")

                return@any true
            }

            return@any false
        }

        return typesMatch
    }

    override fun toString(): String = "object"
}

class AnyFunctionType: Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())

    override fun matches(v: RuntimeVal): Boolean = v is FunctionValue
}

open class FunctionType(private val returns: Type, val args: HashSet<Pair<Type, Byte>>): Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyFunctionType())
    override val subTypes: HashSet<out Type> = hashSetOf()

    override fun matches(v: RuntimeVal): Boolean {
        if (v !is FunctionValue) return false
        if (!(checkSubTypesRecursive(returns, v.name.second) || checkSuperTypesRecursive(returns, v.name.second))) return false

        // TODO: check type params too when implemented

        // Check parameter types

        return v.params.any { (name, type) ->
            val expectType = args.sortedBy { it.second }[name.second.toInt()].first

            checkSubTypesRecursive(expectType, type) || checkSuperTypesRecursive(expectType, type)
        }
    }

    override fun toString(): String {
        return "(${args.sortedBy { it.second }.joinToString(", ") { it.first.toString() }}) -> $returns"
    }
}

data class NullableTypeWrapper(val baseType: Type) : Type() {
    override val superTypes: HashSet<out Type> = hashSetOf()
    override val subTypes: HashSet<out Type> = hashSetOf(baseType)

    override fun matches(v: RuntimeVal): Boolean = baseType matches v || NullType() matches v
    override fun toString(): String = "?$baseType"
}

data class UnionType(val first: Type, val second: Type): Type() {
    override val superTypes: HashSet<out Type> = hashSetOf()
    override val subTypes: HashSet<out Type> = hashSetOf(first, second)

    override fun matches(v: RuntimeVal): Boolean = first matches v || second matches v
}

data class CombinedType(val types: HashSet<Type>): Type() {
    override val superTypes: HashSet<out Type> = hashSetOf()
    override val subTypes: HashSet<out Type> = types

    constructor(vararg types: Type) : this(hashSetOf(*types))

    override fun matches(v: RuntimeVal): Boolean = types.any { it matches v }

}

data class UnresolvedType(val expr: TypeExpr, val name: String): Type() {
    fun isMatch(predicate: (TypeExpr) -> Boolean) = predicate(expr)
}

val types = hashMapOf(
    "str" to StringType(),
    "number" to NumberType(),
    "object" to AnyObjectType(),
    "bool" to BoolType(),
    "any" to AnyType(),
    "null" to NullType(),
    "promise" to PromiseType(),
    "integer" to IntegerType()
)

fun getStrictestSubType(base: Type, v: RuntimeVal, depth: Int = 0): Pair<Type, Int> {
    if (base.subTypes.size == 0) return base to depth

    val matches = hashSetOf(base to -2)

    base.subTypes.forEach {
        if (it matches v) {
            if (checkSuperTypesRecursive(it, v.type ?: NeverType())) {
                matches.add(
                    getStrictestSubType(v.type!!, v, depth + 1)
                )
            } else {
                matches.add(
                    getStrictestSubType(it, v, depth + 1)
                )
            }
        } else if (v.type != null) {
            matches.add(
                getStrictestSubType(v.type!!, v, depth + 1)
            )
        }
    }

    return matches.maxBy { it.second }
}

fun checkSubTypesRecursive(want: Type, has: Type): Boolean {
    val check = want != has && (has !in want.subTypes)

    if (check) return has.subTypes.any { checkSubTypesRecursive(want, it) }

    return true
}

fun checkSuperTypesRecursive(want: Type, has: Type): Boolean {
    val check = want != has && (want !in has.superTypes)

    if (check) return has.superTypes.any { checkSuperTypesRecursive(want, it) }

    return true
}

fun checkTypeRecursive(want: Type, have: Type): Boolean {
    if (have matchesType want) return true

    return checkSubTypesRecursive(want, have) || checkSuperTypesRecursive(want, have)
}

fun typeEval(t: TypeExpr, env: Environment): Type = when (t) {
    is FuncType -> object : FunctionType(typeEval(t.returnType, env), t.argTypes.map { (type, index) -> typeEval(type, env) to index }.toHashSet()) {
        override fun toString(): String = t.toString()
    }

    is StringLiteral -> object: Type() {
        override val superTypes: HashSet<out Type> = hashSetOf(StringType())
        override val subTypes: HashSet<Type> = hashSetOf()

        override fun matches(v: RuntimeVal): Boolean = v.value == t.value
        override fun toString(): String = t.value
    }

    is NumberLiteral -> object: Type() {
        override val superTypes: HashSet<out Type> = hashSetOf(NumberType())
        override val subTypes: HashSet<Type> = hashSetOf()

        override fun matches(v: RuntimeVal): Boolean = v.value == t.value
        override fun toString(): String = t.value.toString()
    }

    is IdentType -> env.getTypeOrNull(t.name) ?: UnresolvedType(t, t.name)
    is NullableType -> NullableTypeWrapper(typeEval(t.base, env))
    else -> AnyType()
}