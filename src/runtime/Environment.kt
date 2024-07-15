package runtime

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import errors.Error
import errors.IncorrectTypeError
import frontend.Parser
import globalCoroutineScope
import globalVars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import runtime.types.*
import runtime.types.Function
import serverRunning
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.absolutePathString
import kotlin.math.*
import kotlin.system.exitProcess
import kotlin.time.ExperimentalTime


val nativeFuncType = object : Type {
    override fun matches(v: RuntimeVal): Boolean = v is NativeFnValue
}

fun URI.findParameterValue(key: String): String? {
    return rawQuery.split('&').map {
        val parts = it.split('=')
        val name = parts.firstOrNull() ?: ""
        val value = parts.drop(1).firstOrNull() ?: ""
        Pair(name, value)
    }.firstOrNull { it.first == key }?.second
}

@OptIn(ExperimentalTime::class)
fun makeGlobalEnv(argv: Array<StringVal>): Environment {
    val envP = Environment(null)

    envP.declareVar("true", makeBool(), true)
    envP.declareVar("false", makeBool(false), true)

    envP.declareVar("null", makeNull(), false)

    val io: HashMap<String, Pair<RuntimeVal, Type>> = hashMapOf(
        "File" to makeNativeFnWithType("__std.not_supported_js", 1) { args, _ ->
            val absPath = Paths.get(args[0].value.toString())
            val f = File(absPath.toUri())

            val obj = hashMapOf(
                "readString" to makeNativeFnWithType("__std.not_supported_js", arity = 0) { _, _ ->
                    val future = CompletableFuture<RuntimeVal>()

                    val stream = ValueStream()
                    var acc = ""

                    runBlocking {
                        withContext(Dispatchers.IO) {
                            future.complete(
                                makeString(f.bufferedReader().readText())
                            )
                        }
                    }

                    globalCoroutineScope.launch {
                        f.bufferedReader().useLines {
                            lines->lines.forEach{
                                var line = "> (" + it.length + ") " + it;
                                acc += "$line\n"
                            }
                        }
                    }

                    return@makeNativeFnWithType makeStreamedPromise(stream)
                },
                "readBytes" to makeNativeFnWithType("__std.not_supported_js", arity = 0) {_, _ ->
                    return@makeNativeFnWithType makeNull()
                },
                "close" to makeNativeFnWithType("__std.not_supported_js", arity = 0) { _, _ ->
                    f.reader().close()

                    return@makeNativeFnWithType makeNull()
                },
                "absolutePath" to (makeString(absPath.absolutePathString()) to StringType())
            )

            return@makeNativeFnWithType makeObject(obj)
        },
        "println" to makeNativeFnWithType("console.log", -1, "println") { args, _ ->
            for (arg in args) {
                print(arg.value)
                print(", ")
            }

            println()

            makeNull()
        },
        "print" to makeNativeFnWithType("console.log", -1, "print") { args, _ ->
            for (arg in args) {
                print(arg.value)
                print(", ")
            }

            makeNull()

            return@makeNativeFnWithType makeNull()
        },
        "eprintln" to makeNativeFnWithType("console.error", -1, "eprintln") { args, _ ->
            for (arg in args) {
                System.err.print(arg.value)
                System.err.print(", ")
            }

            System.err.println()

            makeNull()
        },
        "eprint" to makeNativeFnWithType("console.error", -1, "eprint") { args, _ ->
            for (arg in args) {
                System.err.print(arg.value)
                System.err.print(", ")
            }

            makeNull()
        },
        "readln" to makeNativeFnWithType("__std.not_supported_js", 0, "readln") { _, _ ->
            makeString(
                readln()
            )
        },
        "exit" to makeNativeFnWithType("__std.exit", jvmName = "exit") { args, _ ->
            val code = (args[0] as NumberVal).value

            exitProcess(code.toInt())
        }
    )

    val data: HashMap<String, Pair<RuntimeVal, Type>> = hashMapOf("join" to makeNativeFnWithType("__std.join_obj", 2) { args, _ ->
            makeString(
                (args[0] as ObjectVal).value.values.joinToString((args[1] as StringVal).value)
            )
        },
        "keysOf" to makeNativeFnWithType("Object.keys", 1) { args, _ ->
            val of = args[0] as ObjectVal
            val paired = mutableListOf<Pair<String, Pair<StringVal, StringType>>>()

            of.value.entries.forEachIndexed { index, (key, _) ->
                paired.add("$index" to (makeString(key) to StringType()))
            }

            val obj: HashMap<String, Pair<RuntimeVal, Type>> = hashMapOf(*paired.toTypedArray())

            return@makeNativeFnWithType makeObject(obj)
        },
        "valuesOf" to makeNativeFnWithType("Object.values", 1) { args, _ ->
            val of = args[0] as ObjectVal
            val paired = mutableListOf<Pair<String, Pair<RuntimeVal, Type>>>()

            of.value.entries.forEachIndexed { index, (_, value) ->
                paired.add("$index" to value)
            }

            val obj: HashMap<String, Pair<RuntimeVal, Type>> = hashMapOf(*paired.toTypedArray())

            return@makeNativeFnWithType makeObject(obj)
        }
    )

    val net: HashMap<String, Pair<RuntimeVal, Type>> = hashMapOf(
        "post" to makeNativeFnWithType("fetch", 2) { args, _ ->
            val gson = Gson()
            gson.toJson((args[1] as ObjectVal).value).toRequestBody("application/json".toMediaTypeOrNull())

            val future = CompletableFuture<RuntimeVal>()
            val client = OkHttpClient()
            val req = Request.Builder().url((args[0] as StringVal).value).build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    future.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    future.complete(makeString(response.body?.string() ?: ""))
                }

            })

            return@makeNativeFnWithType makePromise(future)
        },
        "get" to makeNativeFnWithType("fetch") { args, _ ->
            val future = CompletableFuture<RuntimeVal>()
            val client = OkHttpClient()
            val req = Request.Builder().url((args[0] as StringVal).value).build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    future.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    future.complete(makeString(response.body?.string() ?: ""))
                }

            })

            return@makeNativeFnWithType makePromise(future)
        }
    )

    val math = hashMapOf(
        "cos" to makeNativeFnWithType("Math.cos") { args, _ ->
            return@makeNativeFnWithType makeNumber(cos((args[0] as NumberVal).value))
        },
        "sin" to makeNativeFnWithType("Math.sin") { args, _ ->
            return@makeNativeFnWithType makeNumber(sin((args[0] as NumberVal).value))
        },
        "rand" to makeNativeFnWithType("Math.random", 2) { args, _ ->
            val min = (args[0] as NumberVal).value
            val max = (args[1] as NumberVal).value
            val r = floor(Math.random() * (max - min + 1) + min)

            return@makeNativeFnWithType makeNumber(r)
        },
        "pi" to (makeNumber(Math.PI) to NumberType())
    )

    val reflectionFuncType = object : Type {
        override fun matches(v: RuntimeVal): Boolean {
            return v is ObjectVal &&
                    AnyFunctionType() matches (v.value["value"]?.first ?: makeNull()) &&
                    NumberType() matches (v.value["arity"]?.first ?: makeNull())
        }

        override fun toString(): String = "reflection-func"
    }

    val reflectionGlobalFuncsType = object : Type {
        override fun matches(v: RuntimeVal): Boolean {
            return v is ObjectVal && v.value.values.all { reflectionFuncType matches it.first }
        }

        override fun toString(): String = "reflection-files"
    }

    val reflectionFileType = object : Type {
        override fun matches(v: RuntimeVal): Boolean {
            return v is ObjectVal && reflectionGlobalFuncsType matches (v.value["globalFunctions"]?.first ?: makeNull())
        }

        override fun toString(): String = "reflection-file"
    }

    val reflection = hashMapOf<String, Pair<RuntimeVal, Type>>(
        "fromSource" to makeNativeFnWithType("__std.not_supported_js") { args, _ ->
            val sourceCode = args[0] as StringVal
            val env = makeGlobalEnv(arrayOf())

            evaluate(
                Parser().produceAST(sourceCode.value),
                env
            )

            val functions: List<Pair<String, Pair<RuntimeVal, Type>>> = env.variables.filter { it.value is Function }.map {
                it.name to (makeObject(hashMapOf(
                    "value" to (it.value to AnyFunctionType()),
                    "arity" to (makeNumber((it.value as Function).arity.toDouble()) to AnyFunctionType())
                )) to reflectionFuncType)
            }

            val reflected = hashMapOf<String, Pair<RuntimeVal, Type>>(
                "globalFunctions" to (makeObject(hashMapOf(*functions.toTypedArray())) to reflectionGlobalFuncsType)
            )

            return@makeNativeFnWithType makeObject(reflected)
        },
        "fromFile" to makeNativeFnWithType("__std.not_supported_js") { args, _ ->
            val sourceCode = Files.readString(Paths.get((args[0] as StringVal).value))
            val env = makeGlobalEnv(arrayOf())

            evaluate(
                Parser().produceAST(sourceCode),
                env
            )

            val functions: List<Pair<String, Pair<RuntimeVal, Type>>> = env.variables.filter { it.value is Function }.map {
                it.name to (makeObject(hashMapOf(
                    "value" to (it.value to AnyFunctionType()),
                    "arity" to (makeNumber((it.value as Function).arity.toDouble()) to AnyFunctionType())
                )) to reflectionFuncType)
            }

            val reflected = hashMapOf<String, Pair<RuntimeVal, Type>>(
                "globalFunctions" to (makeObject(hashMapOf(*functions.toTypedArray())) to reflectionGlobalFuncsType)
            )

            return@makeNativeFnWithType makeObject(reflected)
        }
    )

    envP.declareVar("io", makeObject(io), true)
    envP.declareVar("net", makeObject(net), true)
    envP.declareVar("data", makeObject(data), true)
    envP.declareVar("math", makeObject(math), true)
    envP.declareVar("reflection", makeObject(reflection), true)

    envP.declareVar("time", makeNativeFn("Date.now", 0) { _, _ ->
        return@makeNativeFn makeNumber(Date().time.toDouble())
    }, true)

    val args = HashMap<String, Pair<RuntimeVal, Type>>()

    argv.forEachIndexed { index, str ->
        args["$index"] = str to StringType()
    }

    envP.declareVar("argv", makeObject(args), true)

    class ServerHandlerType : Type {
        override fun matches(v: RuntimeVal): Boolean =
            v is ObjectVal
                    && v.value.all { it.value.first is FunctionValue }
                    && v.value["index"] != null

        override fun toString(): String = "server-handler"
    }

    class ServerResponseType : Type {
        override fun matches(v: RuntimeVal): Boolean =
            v is ObjectVal
                    && NumberType() matches (v.value["code"]?.first ?: makeNull())
                    && StringType() matches (v.value["response"]?.first ?: makeNull())
                    && StringType() matches (v.value["mimeType"]?.first ?: makeNull())

        override fun toString(): String = "server-response"
    }

    fun globalHandle(exchange: HttpExchange?, handler: RuntimeVal) {
        if (exchange == null) return

        val requestInfo = hashMapOf<String, Pair<RuntimeVal, Type>>(
            "localHostname" to (makeString(exchange.localAddress.hostName) to StringType()),
            "body" to (makeString(String(exchange.requestBody.readAllBytes())) to StringType()),
            "method" to (makeString(exchange.requestMethod) to StringType()),
            "path" to (makeString(exchange.requestURI.path) to StringType()),
            "param" to makeNativeFnWithType("__std.not_supported_js", 1) { args, _ ->
                return@makeNativeFnWithType makeString(exchange.requestURI.findParameterValue((args[0] as StringVal).value) ?: "")
            }
        )

        val handlerArgs = listOf(makeObject(requestInfo))

        if (handler !is FunctionValue) {
            IncorrectTypeError(FunctionType(), handler).raise()
        }

        if (handler.arity != handlerArgs.size) {
            throw RuntimeException("${handler.name} expected ${handler.arity} arguments, instead got ${handlerArgs.size}.")
        }

        val scope = Environment(envP)

        for ((name, type) in handler.params) {
            if (!(type matches handlerArgs[name.second.toInt()])) {
                Error<Nothing>(
                    "Parameter ${name.first} at position ${name.second} of function ${(handler as FunctionValue).name.first ?: "ANONYMOUS"} required a type of ${type}.",
                    ""
                ).raise()
            }

            scope.declareVar(name.first, handlerArgs[name.second.toInt()], true)
        }

        var result: RuntimeVal = makeNull()

        for (statement in (handler as FunctionValue).value) {
            result = evaluate(statement, scope)
        }

        if (!(ServerResponseType() matches result) || result !is ObjectVal) {
            IncorrectTypeError(ServerResponseType(), result)
                .raise()
        }

        val code = (result.value["code"]?.first as? NumberVal)?.value?.roundToInt() ?: 404
        val response = (result.value["response"]?.first as? StringVal)?.value
            ?: "The server did not send a correct response."
        val mimeType = (result.value["mimeType"]?.first as? StringVal)?.value ?: "text/plain"

        exchange.sendResponseHeaders(code, response.toByteArray().size.toLong())
        exchange.responseHeaders["Content-Type"] = Collections.singletonList(mimeType)
        val os: OutputStream = exchange.responseBody

        os.write(response.toByteArray())
        os.close()
    }

    (envP.getVar("net").value as ObjectVal).value["serve"] = makeNativeFnWithType("__std.not_supported_js", 3) { args, _ ->
        val hostname = (args[0] as? StringVal ?: IncorrectTypeError(StringType(), args[0]).raise()).value
        val port = (args[1] as? NumberVal ?: IncorrectTypeError(NumberType(), args[1]).raise()).value
        val handlers = (args[2] as? ObjectVal ?: IncorrectTypeError(ServerHandlerType(), args[2]).raise()).value

        println(handlers.entries.joinToString { "/${it.key} -> ${it.value.first.hashCode()}" })

        val server = HttpServer.create(
            InetSocketAddress(hostname, port.roundToInt()),
            0
        )

        for ((route, handlerPair) in handlers) {
            val handler = handlerPair.first

            val requestHandler = HttpHandler { exchange ->
                globalCoroutineScope.launch {
                    globalHandle(exchange, handler)
                }
            }

            if (route == "index") {
                server.createContext("/", requestHandler)

                continue
            }

            server.createContext("/$route", requestHandler)
        }

        server.executor = null
        serverRunning = true
        server.start()

        return@makeNativeFnWithType makeNull()
    }

    return envP
}

fun makeGlobalCompilationEnv(): CompilationEnvironment {
    val envP = CompilationEnvironment(null)

    envP.declareVar("true", true)
    envP.declareVar("false", true)
    envP.declareVar("null", false)
    envP.declareVar("io", true)
    envP.declareVar("net", true)
    envP.declareVar("data", true)
    envP.declareVar("math", true)
    envP.declareVar("time", true)
    envP.declareVar("argv", true)

    return envP
}

open class Variable(
    open var constant: Boolean = true,
    open val name: String,
    open var value: RuntimeVal,
    open val lock: ReentrantLock,
    private val bindings: HashSet<Variable> = hashSetOf()
) {

    fun lock() = lock.lock()
    fun unlock() = lock.unlock()

    /**
     * 'Peg' a variable to another so that they both always have the same value
     */
    fun peg(variable: Variable) {
        if (this in variable.bindings) {
            throw ClassCircularityError(
                "Cannot peg ${variable.name} to ${this.name} since ${variable.name} is pegged to ${this.name}"
            )
        }

        this.bindings.add(variable)
    }

    /**
     * 'Unpeg' a variable from another so that they no longer both always have the same value
     */
    fun unpeg(variable: Variable) {
        if (this !in variable.bindings) {
            throw IllegalAccessError("Cannot unpeg ${variable.name} from ${this.name} since they are not pegged.")
        }

        this.bindings.remove(variable)
    }

    /**
     * THIS FUNCTION DOES NOT GUARANTEE TYPE SAFETY OF THE OLD AND ASSIGNED VALUE */
    fun mutate(new: RuntimeVal, file: String) {
        if (this.constant) {
            Error<Nothing>(
                "Cannot mutate ${this.name} since it is constant. Try declaring it with the 'mutable' keyword instead.",
                file
            )
                .raise()
        }

        this.value = new

        this.bindings.forEach {
            if (!it.constant) it.mutate(new, file)
        }
    }
}

class CompilationVariable(
    var constant: Boolean = true,
    val name: String,
    val index: Int,
    private val bindings: HashSet<CompilationVariable> = hashSetOf()
) {

    /**
     * 'Peg' a variable to another so that they both always have the same value
     */
    fun peg(variable: CompilationVariable) {
        if (this in variable.bindings) {
            throw ClassCircularityError(
                "Cannot peg ${variable.name} to ${this.name} since ${variable.name} is pegged to ${this.name}"
            )
        }

        this.bindings.add(variable)
    }

    /**
     * 'Unpeg' a variable from another so that they no longer both always have the same value
     */
    fun unpeg(variable: CompilationVariable) {
        if (this !in variable.bindings) {
            throw IllegalAccessError("Cannot unpeg ${variable.name} from ${this.name} since they are not pegged.")
        }

        this.bindings.remove(variable)
    }

}

class Environment(
    private val parent: Environment?,
    val variables: HashSet<Variable> = hashSetOf(),
    val isCoroutine: Boolean = false,
) {

    /**
     * Create a deep copy of this environment.
     * @return A deep copy of this environment and its parent recursively.
     */
    fun copy(): Environment =
        Environment(
            parent?.copy(),
            variables.toHashSet(),
            isCoroutine
        )

    fun declareVar(name: String, value: RuntimeVal, constant: Boolean): Variable {
        if (this.resolve(name) != null) {
            if (isCoroutine) {
                Error<Nothing>("Cannot redeclare variable $name. Try using the ScopeCopy annotation to make a copy of the outer scope.", "")
                    .raise()
            }

            println(this.resolve(name)?.getVarOrNull(name)?.value?.value)

            Error<Nothing>("Cannot redeclare variable $name. Try using the mutable keyword to declare variables.", "")
                .raise()
        }

        val v = Variable(constant, name, value, ReentrantLock())

        this.variables.add(v)

        return v
    }

    @Throws(IllegalAccessException::class)
    fun getVar(name: String): Variable {
        this.variables.forEach {
            if (it.name == name) {
                return@getVar it
            }
        }

        if (this.parent != null) {
            return this.parent.getVar(name)
        }

        throw IllegalAccessException("Variable $name was never declared.")
    }

    private fun getVarOrNull(name: String): Variable? {
        return try {
            getVar(name)
        } catch (e: Exception) {
            null
        }
    }

    fun clear() {
        variables.clear()
    }

    fun assignVar(name: String, value: RuntimeVal): RuntimeVal {
        this.resolve(name)
            ?: throw IllegalAccessException("Variable $name cannot be reassigned as it was never declared.")

        this.getVar(name).mutate(value, "")

        return value
    }

    fun lookupVar(name: String): RuntimeVal {
        val env = this.resolve(name)
            ?: throw IllegalAccessException(
                "Variable '$name' cannot be accessed as it was never declared. Did you mean '${this.findClosestVar(name)}'?"
            )

        return env.getVarOrNull(name)?.value ?: makeNull()
    }


    private fun findClosestVar(word: String): String? {
        var closestWord: String? = null
        var minDistance = Int.MAX_VALUE

        for (w in this.variables.map { it.name }) {
            val distance = this.calculateDistance(word, w)
            if (distance < minDistance) {
                minDistance = distance
                closestWord = w
            }
        }

        return closestWord
    }

    private fun calculateDistance(word1: String, word2: String): Int {
        val length1 = word1.length
        val length2 = word2.length
        val matrix = Array(length1 + 1) { IntArray(length2 + 1) }

        for (i in 0..length1) {
            matrix[i][0] = i
        }
        for (j in 0..length2) {
            matrix[0][j] = j
        }

        for (i in 1..length1) {
            for (j in 1..length2) {
                val cost = if (word1[i - 1] == word2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
                )
            }
        }

        return matrix[length1][length2]
    }

    fun resolve(name: String): Environment? {
        return if (this.getVarOrNull(name) != null) {
            this
        } else if (this.parent != null) {
            this.parent.resolve(name)
        } else {
            null
        }
    }

    fun toReadonly(): Environment = Environment(
        this.parent,
        this.variables.map {
            val c = it
            c.constant = true
            c
        }.toHashSet(),
        isCoroutine
    )
}

class CompilationEnvironment(
    private val parent: CompilationEnvironment?,
    val variables: HashSet<CompilationVariable> = hashSetOf()
) {
    fun declareVar(name: String, constant: Boolean, idx: Int = -1) {
        if (this.resolve(name) != null) {
            throw IllegalAccessException("Cannot redeclare variable $name.")
        }

        this.variables.add(CompilationVariable(constant, name, idx))
    }

    fun getVar(name: String): CompilationVariable {
        this.variables.forEach { if (it.name == name) return@getVar it }

        if (this.parent != null) {
            return this.parent.getVar(name)
        }

        throw IllegalAccessException("Variable $name was never declared.")
    }

    fun getSize(): Int {
        if (this.parent == null) {
            return this.variables.size
        }

        return this.variables.size + this.parent.getSize()
    }

    private fun getVarOrNull(name: String): CompilationVariable? {
        this.variables.forEach { if (it.name == name) return@getVarOrNull it }

        if (this.parent != null) {
            return this.parent.getVarOrNull(name)
        }

        return null
    }

    private fun findClosestVar(word: String): String? {
        var closestWord: String? = null
        var minDistance = Int.MAX_VALUE

        for (w in this.variables.map { it.name }) {
            val distance = this.calculateDistance(word, w)
            if (distance < minDistance) {
                minDistance = distance
                closestWord = w
            }
        }

        return closestWord
    }

    private fun calculateDistance(word1: String, word2: String): Int {
        val length1 = word1.length
        val length2 = word2.length
        val matrix = Array(length1 + 1) { IntArray(length2 + 1) }

        for (i in 0..length1) {
            matrix[i][0] = i
        }
        for (j in 0..length2) {
            matrix[0][j] = j
        }

        for (i in 1..length1) {
            for (j in 1..length2) {
                val cost = if (word1[i - 1] == word2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
                )
            }
        }

        return matrix[length1][length2]
    }

    private fun resolve(name: String): CompilationEnvironment? {
        return if (this.getVarOrNull(name) != null) {
            this
        } else if (this.parent != null) {
            this.parent.resolve(name)
        } else {
            null
        }
    }

    fun toReadonly(): CompilationEnvironment = CompilationEnvironment(
        this.parent,
        this.variables.map {
            val c = it
            c.constant = true
            c
        }.toHashSet()
    )
}