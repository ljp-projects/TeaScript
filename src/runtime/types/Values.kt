package runtime.types

import com.sun.jdi.connect.spi.ClosedConnectionException
import frontend.*
import runtime.Environment
import runtime.evaluate
import runtime.nativeFuncType
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.ExperimentalTime

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

class ValueStream {
    private val dataQueue: Queue<RuntimeVal> = ArrayDeque()
    private val lock = ReentrantLock()
    private var _closed = false

    var closed: Boolean
        get() = _closed
        private set(n) {
            _closed = n
        }

    fun isEmpty(): Boolean = dataQueue.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()

    fun close() {
        lock.lock()
        closed = true
    }

    // Read the first item in the dataQueue or wait until it is not empty
    fun readBlocking(): RuntimeVal {
        if (closed && dataQueue.isEmpty()) {
            throw ClosedConnectionException("This stream has been closed and is empty.")
        }

        if (dataQueue.isEmpty()) {
            readBlocking()
        }

        synchronized(lock) {
            return dataQueue.remove()
        }
    }

    /*
    io -> File
    yield func read() {
        # ... start reading line-by-line
        yield line
    }

    yield func sum(array: [number]) {
        foreach value from array {
            yield value
        }
    }
    */

    // Read the first item in the dataQueue or null if it is empty
    fun readOrNull(): RuntimeVal? {
        if (dataQueue.isEmpty()) {
            return null
        }

        synchronized(lock) {
            return dataQueue.remove()
        }
    }

    fun write(value: RuntimeVal) {
        synchronized(lock) {
            dataQueue.offer(value)
        }
    }
}

interface RuntimeVal {
    val kind: String
    val value: Any
}

interface IterableVal : RuntimeVal
interface AwaitableVal : RuntimeVal

fun RuntimeVal.toCommon(): String {
    return if (this is StringVal && this.value.toIntOrNull() != null) {
        this.value + ".0"
    } else if (this is NumberVal) {
        if (value % 1 == 0.0) {
            "${value.toInt()}.0"
        } else {
            value.toString()
        }
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

fun RuntimeVal.asString(): String {
    return if (this is StringVal) {
        this.toCommon()
    } else if (this is NumberVal) {
        val numberString: String = if (this.value % 1 == 0.0) {
            this.value.toInt().toString()
        } else {
            this.value.toString()
        }

        numberString
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
        require(this.kind == "null") { "Key can't be ${this.kind}." }
    }
}

fun makeNull() = object : NullVal(value = "void 0") {}

abstract class BoolVal(
    final override val kind: String = "bool",
    override val value: Boolean
) : RuntimeVal {
    init {
        require(this.kind == "bool") { "Key can't be ${this.kind}." }
    }
}

fun makeBool(b: Boolean = true) = object : BoolVal(value = b) {}

abstract class PromiseVal(
    final override val kind: String = "promise",
    override val value: CompletableFuture<RuntimeVal>
) : RuntimeVal, AwaitableVal {
    init {
        require(this.kind == "promise") { "Key can't be ${this.kind}." }
    }
}

abstract class StreamedPromiseVal(
    final override val kind: String = "streamed-promise",
    override val value: ValueStream
) : IterableVal, AwaitableVal {
    init {
        require(this.kind == "streamed-promise") { "Key can't be ${this.kind}." }
    }
}

fun makePromise(f: CompletableFuture<RuntimeVal>) = object : PromiseVal(value = f) {}
fun makeStreamedPromise(s: ValueStream) = object : StreamedPromiseVal(value = s) {}

abstract class NumberVal(
    final override val kind: String = "number",
    override val value: Double
) : RuntimeVal {
    init {
        require(this.kind == "number") { "Key can't be ${this.kind}." }
    }
}

fun makeNumber(n: Double) = object : NumberVal(value = n) {}

abstract class StringVal(
    final override val kind: String = "str",
    override val value: String
) : RuntimeVal {
    init {
        require(this.kind == "str") { "Key can't be ${this.kind}." }
    }
}

@OptIn(ExperimentalTime::class)
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

                        final += evaluate(Parser().produceAST(expr), env).value
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

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

open class ImportVal(
    final override val kind: String = "import",
    override val value: Environment,
    val imports: HashSet<String>
) : RuntimeVal {
    init {
        require(this.kind == "import") { "Key can't be ${this.kind}." }
    }
}

fun makeObject(h: HashMap<String, Pair<RuntimeVal, Type>>, kind: String = "object") =
    object : ObjectVal(kind, h) {}

typealias FunctionCall = (args: List<RuntimeVal>, env: Environment) -> RuntimeVal

interface Function: RuntimeVal {
    val arity: Int
}

abstract class NativeFnValue(
    final override val kind: String = "native-func",
    override val arity: Int,
    val name: String,
    val jvmName: String,
    override val value: FunctionCall
) : Function {
    init {
        require(this.kind == "native-func") { "Key can't be ${this.kind}." }
    }
}

fun makeNativeFn(name: String, arity: Int = 1, jvmName: String = "", f: FunctionCall) =
    object : NativeFnValue(arity = arity, value = f, name = name, jvmName = jvmName) {}

fun makeNativeFnWithType(name: String, arity: Int = 1, jvmName: String = "", f: FunctionCall): Pair<NativeFnValue, Type> =
    object : NativeFnValue(arity = arity, value = f, name = name, jvmName = jvmName) {} to nativeFuncType

open class FunctionValue(
    final override val kind: String = "func",
    open val name: Pair<String?, Type>,
    val params: HashMap<Pair<String, Byte>, Type>,
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

    fun hasModifier(type: ModifierType, value: String = "YES") =
        modifiers.any { it.type == type && it.value == value }

    private fun static(): Boolean = this.modifiers.any { it.type == ModifierType.Static }
}

open class ArrayValue(
    final override val kind: String,
    val type: Type,
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
) : RuntimeVal {
    init {
        require(this.kind == "for") { "Key can't be ${this.kind}." }
    }

    val async: Boolean
        get() = this.modifiers.any { it.type == ModifierType.Synchronised }
}

abstract class AwaitValue(
    final override val kind: String = "await",
    val param: Identifier,
    val obj: AwaitableVal,
    val declEnv: Environment,
    override val value: Block,
    val async: Boolean,
) : RuntimeVal {
    init {
        require(this.kind == "await") { "Key can't be ${this.kind}." }
    }
}

abstract class AfterValue(
    final override val kind: String = "after",
    val ms: RuntimeVal,
    val declEnv: Environment,
    override val value: Block,
    val async: Boolean,
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

abstract class IfValue(
    final override val kind: String = "if",
    val cond: RuntimeVal,
    val declEnv: Environment,
    override val value: Block,
    val modifiers: Set<Modifier>,
    val otherwise: Block?,
    val orStmts: ArrayDeque<OrDecl>,
) : RuntimeVal {
    init {
        require(this.kind == "if") { "Key can't be ${this.kind}." }
    }

    val async: Boolean
        get() = this.modifiers.any { it.type == ModifierType.Synchronised }
}