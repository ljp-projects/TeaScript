package runtime.types

import frontend.*
import runtime.CompilationEnvironment
import java.util.*

abstract class NativeCompilationFnValue(
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

fun makeNativeCompilationFn(name: String, arity: Int = 1, jvmName: String = "", f: FunctionCall) =
    object : NativeCompilationFnValue(arity = arity, value = f, name = name, jvmName = jvmName) {}


/**
 * The function value to use in compilation.
 * @property name The name of the function.
 * @property static Check if any of the modifiers include a 'static' modifier.
 * @since v1.0.0-beta.3
 */
open class CompilationFunctionValue(
    final override val kind: String = "func",
    val name: Pair<String?, Type>,
    val params: HashMap<Pair<String, Byte>, Type>,
    val declEnv: CompilationEnvironment,
    override val value: ParameterBlock,
    val modifiers: Set<Modifier>
) : RuntimeVal, Iterable<Statement> {
    init {
        require(this.kind == "func") { "Key can't be ${this.kind}." }
    }

    val mutating
        get() = mutating()

    val private
        get() = private()

    val coroutine
        get() = coroutine()

    val promise
        get() = promise()

    val static
        get() = static()

    private fun private() = this.modifiers.any { it.type == ModifierType.Private }

    private fun coroutine(): Boolean = this.modifiers.none { it.type == ModifierType.Synchronised }

    private fun promise(): Boolean = this.modifiers.any { it.type == ModifierType.Promise }

    private fun mutating(): Boolean = this.modifiers.any { it.type == ModifierType.Mutating }

    private fun static(): Boolean = this.modifiers.any { it.type == ModifierType.Static }

    override fun iterator(): Iterator<Statement> = value.iterator()
}

abstract class CompilationClassValue(
    final override val kind: String,
    val name: Pair<String, String>,
    val params: ArrayDeque<Pair<String, String>>,
    val declEnv: CompilationEnvironment,
    override val value: List<Statement>,
    val arity: Int,
) : RuntimeVal {
    init {
        require(kind == name.first) { "Key can't be $kind, mut be ${name.first}." }
    }
}

open class CompilationForValue(
    final override val kind: String = "for",
    val param: Identifier,
    val obj: RuntimeVal,
    val declEnv: CompilationEnvironment,
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

abstract class CompilationAwaitValue(
    final override val kind: String = "await",
    val param: Identifier,
    val obj: PromiseVal,
    val declEnv: CompilationEnvironment,
    override val value: List<Statement>,
    val async: Boolean,
) : RuntimeVal {
    init {
        require(kind == "await") { "Key can't be $kind." }
    }
}

abstract class CompilationAfterValue(
    final override val kind: String = "after",
    val ms: RuntimeVal,
    val declEnv: CompilationEnvironment,
    override val value: ArrayDeque<Statement>,
    val async: Boolean,
) : RuntimeVal {
    init {
        require(kind == "after") { "Key can't be $kind." }
    }
}

fun makeCompilationAfter(ms: RuntimeVal, env: CompilationEnvironment, value: ArrayDeque<Statement>, sync: Boolean) =
    object : CompilationAfterValue(
        ms = ms,
        declEnv = env,
        value = value,
        async = !sync
    ) {}

abstract class CompilationIfValue(
    final override val kind: String = "if",
    val cond: RuntimeVal,
    val declEnv: CompilationEnvironment,
    override val value: Block,
    val modifiers: Set<Modifier>,
    val otherwise: Block?,
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