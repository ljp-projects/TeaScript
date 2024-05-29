package runtime

import frontend.*
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashSet

interface RuntimeVal {
    val kind: String
    val value: Any
}

fun RuntimeVal.toCommon(): String {
    return if (this is StringVal && this.value.toIntOrNull() != null) {
        this.value + ".0"
    } else {
        "${this.value}"
    }
}

fun RuntimeVal.toFancy(): String {
    return if (this is StringVal) {
        "\"${this.toCommon()}\""
    } else {
        this.toCommon()
    }
}

fun makeAny(kind: String) = object : RuntimeVal {
    override val kind: String = kind
    override val value = when (kind) {
        "number" -> 0.0
        "str" -> ""
        "bool" -> false
        else -> "void 0"
    }
}

abstract class NullVal(
    final override val kind: String = "null",
    override val value: String
) : RuntimeVal {
    init {
        require(kind == "null") { "Key can't be $kind." }
    }
}

fun makeNull() = object : NullVal("null", "void 0") {}

abstract class BoolVal(
    final override val kind: String = "bool",
    override val value: Boolean
) : RuntimeVal {
    init {
        require(kind == "bool") { "Key can't be $kind." }
    }
}

fun makeBool(b: Boolean = true) = object : BoolVal("bool", b) {}

abstract class PromiseVal(
    final override val kind: String = "promise",
    override val value: CompletableFuture<RuntimeVal>
) : RuntimeVal {
    init {
        require(kind == "promise") { "Key can't be $kind." }
    }
}

fun makePromise(f: CompletableFuture<RuntimeVal>) = object : PromiseVal("promise", f) {}

abstract class NumberVal(
    final override val kind: String = "number",
    override val value: Double
) : RuntimeVal {
    init {
        require(kind == "number") { "Key can't be $kind." }
    }
}

fun makeNumber(n: Double) = object : NumberVal("number", n) {}

abstract class StringVal(
    final override val kind: String = "str",
    override val value: String
) : RuntimeVal {
    init {
        require(kind == "str") { "Key can't be $kind." }
    }
}

fun makeString(s: String, env: Environment = Environment(null)): StringVal {
    val str = s.replace("\\n", "\n").replace("\\t", "\t").split("").toMutableList()
    var final = ""

    while (str.isNotEmpty()) {
        when (val c = str.removeFirst()) {
            "\\" -> {
                if (str.removeFirstOrNull() == "{") {
                    var expr = ""

                    while (str.isNotEmpty() && str.firstOrNull() != "}") {
                        expr += str.removeFirst()
                    }

                    str.removeFirstOrNull()

                    final += evaluate(Parser().produceAST(expr), env).value
                }
            }
            else -> final += c
        }
    }

    return object : StringVal(
        "str",
        final
    ) {}
}

abstract class ObjectVal(
    final override val kind: String = "object",
    override val value: Pair<MutableList<String>, MutableList<RuntimeVal>>
) : RuntimeVal {
}

abstract class ImportVal(
    final override val kind: String = "import",
    override val value: Environment,
    val imports: HashSet<String>
) : RuntimeVal {
    init {
        require(kind == "import") { "Key can't be $kind." }
    }
}

fun makeObject(h: Pair<MutableList<String>, MutableList<RuntimeVal>>, kind: String = "object") = object : ObjectVal(kind, h) {}

typealias FunctionCall = (args: List<RuntimeVal>, env: Environment) -> RuntimeVal

abstract class NativeFnValue (
    final override val kind: String = "native-func",
    val arity: Int,
    val name: String,
    val jvmName: String,
    override val value: FunctionCall
) : RuntimeVal {
    init {
        require(kind == "native-func") { "Key can't be $kind." }
    }
}

fun makeNativeFn(name: String, arity: Int = 1, jvmName: String = "", f: FunctionCall) = object : NativeFnValue(arity = arity, value = f, name = name, jvmName = jvmName) {}

open class FunctionValue(
    final override val kind: String = "func",
    val name: Pair<String?, String?>,
    val params: ArrayDeque<Pair<String, String>>,
    val declEnv: Environment,
    override val value: List<Statement>,
    val arity: Int,
    val modifiers: Set<Modifier>
) : RuntimeVal {
    init {
        require(kind == "func") { "Key can't be $kind." }
    }

    val mutating
            get() = mutating()

    val private
        get() = private()

    val coroutine
        get() = coroutine()

    val promise
        get() = promise()

    val suffixes
        get() = suffixes()

    val prefixes
        get() = prefixes()

    val static
        get() = static()

    private fun private(): Boolean {
        return this.modifiers.any { it.type == ModifierType.Private }
    }

    private fun coroutine(): Boolean {
        println(modifiers)

        return this.modifiers.none { it.type == ModifierType.Synchronised }
    }

    private fun promise(): Boolean {
        return this.modifiers.any { it.type == ModifierType.Promise }
    }

    private fun mutating(): Boolean {
        return this.modifiers.any { it.type == ModifierType.Mutating }
    }

    private fun suffixes(): Set<String> {
        return this.modifiers.filter { it.type == ModifierType.Suffix }.map { it.value }.toSet()
    }

    private fun prefixes(): Set<String> {
        return this.modifiers.filter { it.type == ModifierType.Prefix }.map { it.value }.toSet()
    }

    private fun static(): Boolean {
        return this.modifiers.any { it.type == ModifierType.Static }
    }
}

abstract class ClassValue(
    final override val kind: String,
    val name: Pair<String, String>,
    val params: ArrayDeque<Pair<String, String>>,
    val declEnv: Environment,
    override val value: List<Statement>,
    val arity: Int,
) : RuntimeVal {
    init {
        require(kind == name.first) { "Key can't be $kind, mut be ${name.first}." }
    }
}

open class ForValue(
    final override val kind: String = "for",
    val param: Identifier,
    val obj: RuntimeVal,
    val declEnv: Environment,
    override val value: List<Statement>,
    val modifiers: Set<Modifier>,
) : RuntimeVal {
    init {
        require(kind == "for") { "Key can't be $kind." }
    }

    val async: Boolean
        get() {
            return modifiers.any { it.type == ModifierType.Synchronised }
        }
}

abstract class AwaitValue(
    final override val kind: String = "await",
    val param: Identifier,
    val obj: PromiseVal,
    val declEnv: Environment,
    override val value: List<Statement>,
    val async: Boolean,
) : RuntimeVal {
    init {
        require(kind == "await") { "Key can't be $kind." }
    }
}

abstract class AfterValue(
    final override val kind: String = "after",
    val ms: RuntimeVal,
    val declEnv: Environment,
    override val value: ArrayDeque<Statement>,
    val async: Boolean,
) : RuntimeVal {
    init {
        require(kind == "after") { "Key can't be $kind." }
    }
}

fun makeAfter(ms: RuntimeVal, env: Environment, value: ArrayDeque<Statement>, sync: Boolean): AfterValue = object : AfterValue(
    ms = ms,
    declEnv = env,
    value = value,
    async = !sync
) {}

abstract class IfValue(
    final override val kind: String = "if",
    val cond: RuntimeVal,
    val declEnv: Environment,
    override val value: ArrayDeque<Statement>,
    val modifiers: Set<Modifier>,
    val otherwise: ArrayDeque<Statement>?,
    val orStmts: ArrayDeque<OrDecl>,
) : RuntimeVal {
    init {
        require(kind == "if") { "Key can't be $kind." }
    }

    val async: Boolean
        get() {
            return modifiers.any { it.type == ModifierType.Synchronised }
        }
}