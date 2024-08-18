package org.ljpprojects.teascript.runtime.types

import org.ljpprojects.teascript.frontend.*
import org.ljpprojects.teascript.runtime.Environment
import org.ljpprojects.teascript.runtime.evaluate
import org.ljpprojects.teascript.runtime.nativeFuncType
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime

data class Return(val value: RuntimeVal): Error()

/*
@Streamed -> implies @Concurrent
func
    -> start coroutine
        -> does operations
        -> uses 'yield' to add a value to a ValueStream
        -> repeat until we reach the end of func
        -> close ValueStream

    -> return a StreamedPromise
*/

interface RuntimeVal {
    val kind: String
    val value: Any
    val type: Type?
}

interface IterableVal : RuntimeVal
interface AwaitableVal : RuntimeVal

fun RuntimeVal.toCommon(): String {
    return if (this is StringVal && this.value.toIntOrNull() != null) {
        this.value + ".0"
    } else if (this is NumberVal) {
        value.toString()
    } else {
        "${this.value}"
    }
}

fun RuntimeVal.toFancy(): String {
    return when (this) {
        is StringVal -> "\"${this.toCommon()}\""
        is ObjectVal -> {
            "{ " + this.toHashMapStored().value + " }"
        }
        is FunctionValue -> {
            """
                |func ${this.name.first}<${this.typeParams.keys.sortedBy { it.second }.joinToString(", ") { it.first }}> (${this.params.entries.sortedBy { it.key.second }.joinToString(", ") { "${it.key.first}: ${it.value}" }}) -> ${this.name.second} {
                |    CODE
                |}
            """.trimMargin()
        }
        else -> this.toCommon()
    }
}

fun RuntimeVal.asString(): String {
    return when (this) {
        is StringVal -> {
            this.toCommon()
        }

        is NumberVal -> {
            val numberString: String = if (this.value % 1.0.toBigDecimal() == 0.0.toBigDecimal()) {
                this.value.toInt().toString()
            } else {
                this.value.toString()
            }

            numberString
        }

        else -> {
            this.toCommon()
        }
    }
}

fun makeAny(kind: String) = object : RuntimeVal {
    override val kind: String = kind
    override val type: Type? = null
    override val value = when (kind) {
        "number" -> 0.0
        "str" -> ""
        "bool" -> false
        else -> "void 0"
    }
}

open class NullVal(
    final override val kind: String = "null",
    override val type: Type? = NullType(),
    override val value: String
) : RuntimeVal {
    init {
        require(this.kind == "null") { "Key can't be ${this.kind}." }
    }
}

fun makeNull() = object : NullVal(value = "void 0") {}

open class BoolVal(
    final override val kind: String = "bool",
    override val type: Type? = BoolType(),
    override val value: Boolean
) : RuntimeVal {
    init {
        require(this.kind == "bool") { "Key can't be ${this.kind}." }
    }
}

fun makeBool(b: Boolean = true) = object : BoolVal(value = b) {}

open class PromiseVal(
    final override val kind: String = "promise",
    override val type: Type? = PromiseType(),
    override val value: CompletableFuture<RuntimeVal>
) : RuntimeVal, AwaitableVal {
    init {
        require(this.kind == "promise") { "Key can't be ${this.kind}." }
    }
}

fun makePromise(f: CompletableFuture<RuntimeVal>) = PromiseVal(value = f)

open class NumberVal(
    final override val kind: String = "number",
    override val type: Type? = NumberType(),
    override val value: BigDecimal
) : RuntimeVal {
    init {
        require(this.kind == "number") { "Key can't be ${this.kind}." }
    }
}

fun makeNumber(n: BigDecimal) = object : NumberVal(value = n) {}

open class StringVal(
    final override val kind: String = "str",
    override val type: Type? = StringType(),
    override val value: String
) : RuntimeVal {
    init {
        require(this.kind == "str") { "Key can't be ${this.kind}." }
    }
}

fun makeString(s: String, env: Environment = Environment(null)): StringVal {
    val str = s.replace("\\n", "\n").replace("\\t", "\t").split("").toMutableList()
    var final = ""

    while (str.isNotEmpty()) {
        when (val c = str.removeAt(0)) {
            "\\" -> {
                when (str.firstOrNull()) {
                    "{" -> {
                        str.removeAt(0)

                        var expr = ""

                        while (str.isNotEmpty() && str.firstOrNull() != "}") {
                            expr += str.removeAt(0)
                        }

                        str.removeFirstOrNull()

                        final += evaluate(Parser(expr).apply { Parser.tokens = tokenise(expr) }.produceAST(), env).value
                    }
                    else -> Unit
                }
            }

            else -> final += c
        }
    }

    return object : StringVal(
        value = final
    ) {}
}

open class ObjectVal(
    final override val kind: String = "object",
    val env: Environment?,
    override val type: Type? = AnyObjectType(),
    override val value: HashMap<String, Pair<RuntimeVal, Type>>
) : IterableVal {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ObjectVal) return false

        if (kind != other.kind) return false
        if (value != other.value) return false
        //if (hashCode() != other.hashCode()) return false

        return true
    }

    fun toHashMapStored(): ObjectVal {
        val props = value

        env?.variables?.forEach {
            props[it.name] = it.value to (if (it.type == null) getStrictestSubType(AnyType(), it.value).first else it.type!!)
        }

        return ObjectVal(
            env = env,
            type = type,
            value = props
        )
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

open class ImportVal(
    final override val kind: String = "import",
    override val value: Environment,
    override val type: Type? = null,
    val imports: HashSet<String>
) : RuntimeVal {
    init {
        require(this.kind == "import") { "Key can't be ${this.kind}." }
    }
}

fun makeObject(h: HashMap<String, Pair<RuntimeVal, Type>>, kind: String = "object", env: Environment? = null) = ObjectVal(kind, value = h, env = env)

typealias FunctionCall = (args: List<RuntimeVal>, env: Environment) -> RuntimeVal

interface Function: RuntimeVal {
    val arity: Int
}

open class NativeFnValue(
    final override val kind: String = "native-func",
    override val type: Type? = nativeFuncType,
    override val arity: Int,
    val name: String,
    override val value: FunctionCall
) : Function {
    init {
        require(this.kind == "native-func") { "Key can't be ${this.kind}." }
    }
}

fun makeNativeFn(name: String, arity: Int = 1, jvmName: String = "", f: FunctionCall) =
    object : NativeFnValue(arity = arity, value = f, name = name) {}

fun makeNativeFnWithType(name: String, arity: Int = 1, jvmName: String = "", f: FunctionCall): Pair<NativeFnValue, Type> {
    val runtimeVal = NativeFnValue(arity = arity, value = f, name = name)

    return runtimeVal to getStrictestSubType(AnyType(), runtimeVal).first
}

open class FunctionValue(
    final override val kind: String = "func",
    override val type: Type? = AnyFunctionType(),
    open val name: Pair<String?, Type>,
    val params: HashMap<Pair<String, Byte>, Type>,
    val typeParams: HashMap<Pair<String, Byte>, Type>,
    open val declEnv: Environment,
    override val value: ParameterBlock,
    val modifiers: HashSet<Modifier>
) : Function {
    init {
        require(this.kind == "func") { "Key can't be ${this.kind}." }
    }

    val private
        get() = this.private()

    val coroutine
        get() = this.coroutine()

    val promise
        get() = this.promise()

    val static
        get() = this.static()

    override val arity
        get() = this.value.parameters.size

    private fun private(): Boolean = this.modifiers.any { it.type == ModifierType.Private }

    private fun coroutine(): Boolean {
        return this.hasModifier(ModifierType.Annotation, "Concurrent")
    }

    private fun promise(): Boolean = this.modifiers.any { it.type == ModifierType.Promise }

    private fun hasModifier(type: ModifierType, value: String = "YES") =
        modifiers.any { it.type == type && it.value == value }

    private fun static(): Boolean = this.modifiers.any { it.type == ModifierType.Static }
}

class ArrayValue(
    override val kind: String,
    override val type: Type,
    override val value: List<RuntimeVal>,
): IterableVal {
    init {
        require(this.kind == "[$type]") { "The kind of an array must be [$type]" }
    }
}

fun makeArray(type: Type, values: List<RuntimeVal>): ArrayValue =
    ArrayValue("[$type]", type, values)

open class ForValue(
    final override val kind: String = "for",
    val param: Identifier,
    val obj: IterableVal,
    val declEnv: Environment,
    override val value: Block,
    val modifiers: Set<Modifier>,
    override val type: Type? = null
) : RuntimeVal {
    init {
        require(this.kind == "for") { "Key can't be ${this.kind}." }
    }

    val async: Boolean
        get() = this.modifiers.any { it.type == ModifierType.Synchronised }
}

open class AwaitValue(
    final override val kind: String = "await",
    val param: Identifier,
    val obj: AwaitableVal,
    val declEnv: Environment,
    override val value: Block,
    val async: Boolean,
    override val type: Type? = null
) : RuntimeVal {
    init {
        require(this.kind == "await") { "Key can't be ${this.kind}." }
    }
}

open class AfterValue(
    final override val kind: String = "after",
    val ms: RuntimeVal,
    val declEnv: Environment,
    override val value: Block,
    val async: Boolean,
    override val type: Type? = null
) : RuntimeVal {
    init {
        require(this.kind == "after") { "Key can't be ${this.kind}." }
    }
}

fun makeAfter(ms: RuntimeVal, env: Environment, value: Block, sync: Boolean): AfterValue =
    object : AfterValue(
        ms = ms,
        declEnv = env,
        value = value,
        async = !sync
    ) {}

open class IfValue(
    final override val kind: String = "if",
    val cond: RuntimeVal,
    val declEnv: Environment,
    override val value: Block,
    val modifiers: Set<Modifier>,
    val otherwise: Block?,
    val orStmts: ArrayDeque<OrDecl>,
    override val type: Type? = null
) : RuntimeVal {
    init {
        require(this.kind == "if") { "Key can't be ${this.kind}." }
    }

    val async: Boolean
        get() = this.modifiers.any { it.type == ModifierType.Synchronised }
}