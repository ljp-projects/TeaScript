package runtime

import frontend.Identifier
import frontend.OrDecl
import frontend.Statement
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashSet

interface RuntimeVal {
    val kind: String
    val value: Any
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

fun makeString(s: String) =
    object : StringVal(
        "str",
        s.replace("\\n", "\n").replace("\\t", "\t")
    ) {}

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

abstract class FunctionValue(
    final override val kind: String = "func",
    val name: Pair<String?, String?>,
    val params: ArrayDeque<Pair<String, String>>,
    val declEnv: Environment,
    override val value: List<Statement>,
    val coroutine: Boolean,
    val private: Boolean,
    val arity: Int,
    val promise: Boolean,
    val mutating: Boolean,
    val static: Boolean,
    val prefix: String?,
    val suffix: String?
) : RuntimeVal {
    init {
        require(kind == "func") { "Key can't be $kind." }
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

abstract class ForValue(
    final override val kind: String = "for",
    val param: Identifier,
    val obj: RuntimeVal,
    val declEnv: Environment,
    override val value: List<Statement>,
    val async: Boolean,
) : RuntimeVal {
    init {
        require(kind == "for") { "Key can't be $kind." }
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
    val async: Boolean,
    val otherwise: ArrayDeque<Statement>?,
    val orStmts: ArrayDeque<OrDecl>,
) : RuntimeVal {
    init {
        require(kind == "if") { "Key can't be $kind." }
    }
}