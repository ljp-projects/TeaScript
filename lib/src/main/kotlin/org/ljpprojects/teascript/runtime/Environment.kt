package org.ljpprojects.teascript.runtime

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.ljpprojects.teascript.globalCoroutineScope
import org.ljpprojects.teascript.runtime.eval.evalCall
import org.ljpprojects.teascript.runtime.types.*
import org.ljpprojects.teascript.serverRunning
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.math.MathContext
import java.math.RoundingMode
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.absolutePathString
import kotlin.math.*
import kotlin.system.exitProcess

object NetServeTypes {
    object Request: ObjectType(
        hashMapOf(
            "localHostname" to StringType(),
            "requesterHostname" to StringType(),
            "body" to StringType(),
            "method" to StringType(),
            "path" to StringType(),
            "param" to FunctionType(
                NullableTypeWrapper(StringType()),
                hashSetOf( StringType() to 0 )
            )
        )
    ) {
        override fun toString(): String = "HttpRequest"
    }

    object Response: ObjectType(
        hashMapOf(
            "code" to NumberType(),
            "response" to StringType(),
            "mimeType" to StringType()
        )
    ) {
        override fun toString(): String = "HttpResponse"
    }

    object Handler: FunctionType(
        Response,
        hashSetOf( Request to 0 )
    ) {
        override fun toString(): String = "HttpHandler"
    }

    object HandlerArray: ObjectType(
        hashMapOf(
            "index" to Handler
        ),

        hashSetOf(Handler)
    )
}

val nativeFuncType = object : Type() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyType())
    override val subTypes: HashSet<Type> = hashSetOf()

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

fun makeGlobalEnv(argv: Array<StringVal>): Environment {
    val envP = Environment(null)

    envP.declareVar("true", makeBool(), true, BoolType())
    envP.declareVar("false", makeBool(false), true, BoolType())

    envP.declareVar("null", makeNull(), false, NullType())

    val io: HashMap<String, Pair<RuntimeVal, Type>> = hashMapOf(
        "File" to makeNativeFnWithType("__std.not_supported_js", 1) { args, _ ->
            val absPath = Paths.get(args[0].value.toString())
            val f = File(absPath.toUri())

            val obj = hashMapOf(
                "readString" to makeNativeFnWithType("__std.not_supported_js", arity = 0) { _, _ ->
                    val future = CompletableFuture<RuntimeVal>()

                    globalCoroutineScope.launch {
                        f.bufferedReader().use {
                            future.complete(makeString(it.readText()))
                        }
                    }

                    return@makeNativeFnWithType makePromise(future)
                },
                "readBytes" to makeNativeFnWithType("__std.not_supported_js", arity = 0) {_, _ ->
                    return@makeNativeFnWithType makeNull()
                },
                "absolutePath" to (makeString(absPath.absolutePathString()) to StringType())
            )

            return@makeNativeFnWithType makeObject(obj)
        },
        "println" to makeNativeFnWithType("console.log", -1, "println") { args, _ ->
            for (arg in args) {
                print(arg.toFancy())
                print(", ")
            }

            println()

            makeNull()
        },
        "print" to makeNativeFnWithType("console.log", -1, "print") { args, _ ->
            for (arg in args) {
                print(arg.toFancy())
                print(", ")
            }

            makeNull()

            return@makeNativeFnWithType makeNull()
        },
        "eprintln" to makeNativeFnWithType("console.error", -1, "eprintln") { args, _ ->
            for (arg in args) {
                System.err.print(arg.toFancy())
                System.err.print(", ")
            }

            System.err.println()

            makeNull()
        },
        "eprint" to makeNativeFnWithType("console.error", -1, "eprint") { args, _ ->
            for (arg in args) {
                System.err.print(arg.toFancy())
                System.err.print(", ")
            }

            makeNull()
        },
        "readln" to makeNativeFnWithType("__std.not_supported_js", 0, "readln") { _, _ ->
            makeString(
                readln()
            )
        },
        "exitWithMessage" to makeNativeFnWithType("exitWithMessage", jvmName = "exitWithMessage") { args, _ ->
            val msg = args[0] as StringVal

            System.err.println(msg.value)
            exitProcess(1)
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
        "strToBase64" to makeNativeFnWithType("", 1) { args, _ ->
            val str = (args[0] as StringVal).value

            return@makeNativeFnWithType makeString(
                Base64.getEncoder().encodeToString(str.encodeToByteArray())
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
        "get" to makeNativeFnWithType("fetch", arity = 2) { args, _ ->
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
        "sqrt" to makeNativeFnWithType("Math.sqrt", arity = 2) { args, _ ->
            return@makeNativeFnWithType makeNumber((args[0] as NumberVal).value.sqrt(MathContext((args[1] as NumberVal).value.toInt(), RoundingMode.HALF_EVEN)))
        },
        "cos" to makeNativeFnWithType("Math.cos") { args, _ ->
            return@makeNativeFnWithType makeNumber(cos((args[0] as NumberVal).value.toDouble()).toBigDecimal())
        },
        "sin" to makeNativeFnWithType("Math.sin") { args, _ ->
            return@makeNativeFnWithType makeNumber(sin((args[0] as NumberVal).value.toDouble()).toBigDecimal())
        },
        "rand" to makeNativeFnWithType("Math.random", 2) { args, _ ->
            val min = (args[0] as NumberVal).value
            val max = (args[1] as NumberVal).value
            val r = floor((Math.random().toBigDecimal() * (max - min + 1.0.toBigDecimal()) + min).toDouble()).toBigDecimal()

            return@makeNativeFnWithType makeNumber(r)
        },
        "pi" to (makeNumber(Math.PI.toBigDecimal()) to NumberType()),
        "floor" to makeNativeFnWithType("Math.floor") { args, _ ->
            return@makeNativeFnWithType makeNumber((args[0] as NumberVal).value.toBigInteger().toBigDecimal())
        },
        "abs" to makeNativeFnWithType("Math.abs") { args, _ ->
            return@makeNativeFnWithType makeNumber((args[0] as NumberVal).value.abs())
        }
    )

    envP.declareVar("io", makeObject(io), true, AnyObjectType())
    envP.declareVar("net", makeObject(net), true, AnyObjectType())
    envP.declareVar("data", makeObject(data), true, AnyObjectType())
    envP.declareVar("math", makeObject(math), true, AnyObjectType())

    envP.declareVar("time", makeNativeFn("Date.now", 0) { _, _ ->
        return@makeNativeFn makeNumber(Date().time.toBigDecimal())
    }, true, FunctionType(NumberType(), hashSetOf()))

    val args = HashMap<String, Pair<RuntimeVal, Type>>()

    argv.forEachIndexed { index, str ->
        args["$index"] = str to StringType()
    }

    envP.declareVar("argv", makeObject(args), true, AnyObjectType())

    fun globalHandle(exchange: HttpExchange?, handler: RuntimeVal) {
        if (exchange == null) return

        val requestInfo = hashMapOf(
            "localHostname" to (makeString(exchange.localAddress.hostName) to StringType()),
            "requesterHostname" to (makeString(exchange.remoteAddress.hostName) to StringType()),
            "body" to (makeString(String(exchange.requestBody.readAllBytes())) to StringType()),
            "method" to (makeString(exchange.requestMethod) to StringType()),
            "path" to (makeString(exchange.requestURI.path) to StringType()),
            "param" to makeNativeFnWithType("__std.not_supported_js", 1) { args, _ ->
                return@makeNativeFnWithType makeString(exchange.requestURI.findParameterValue((args[0] as StringVal).value) ?: "")
            }
        )

        val handlerArgs = listOf(makeObject(requestInfo))

        require(NetServeTypes.Handler matches handler) {
            throw Error("Expected the handler (inside net.serve handlers object) to be a function.")
        }

        val scope = Environment(envP)

        handler as FunctionValue

        val result: RuntimeVal = evalCall(handlerArgs, fn = handler, env = scope)

        require(NetServeTypes.Response matches result) {
            throw Error("Expected the response of /${handler.name.first} to match the HttpResponse type.")
        }

        result as ObjectVal

        val resultMapped = result.toHashMapStored()

        val code = (resultMapped.value["code"]?.first as? NumberVal)?.value?.toInt() ?: 404
        val response = (resultMapped.value["response"]?.first as? StringVal)?.value
            ?: "The server did not send a correct response."
        val mimeType = (resultMapped.value["mimeType"]?.first as? StringVal ?: throw Error("Why isn't mimeType a 'str'? (got type ${result.value["mimeType"]?.first?.type}, value ${result.value["mimeType"]?.first?.toFancy()})")).value

        exchange.sendResponseHeaders(code, response.toByteArray().size.toLong())
        exchange.responseHeaders["Content-Type"] = Collections.singletonList(mimeType)
        val os: OutputStream = exchange.responseBody

        os.write(response.toByteArray())
        os.close()
    }

    (envP.getVar("net").value as ObjectVal).value["serve"] = makeNativeFnWithType("__std.not_supported_js", 3) { args, _ ->
        val hostname = args[0]
        val port = args[1]
        val handlers = args[2]

        require(StringType() matches hostname) {
            throw Error("Expected parameter at index 0 of function 'net.serve' to be a string.")
        }

        require(IntegerType() matches port) {
            throw Error("Expected parameter at index 1 of function 'net.serve' to be an integer.")
        }

        require(NetServeTypes.HandlerArray matches handlers) {
            throw Error("Expected parameter at index 2 of function 'net.serve' to be an integer.")
        }

        hostname as StringVal
        port as NumberVal
        handlers as ObjectVal

        val server = HttpServer.create(
            InetSocketAddress(hostname.value, port.value.toInt()),
            0
        )

        for ((route, handlerPair) in handlers.toHashMapStored().value) {
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

            println(requestHandler)

            server.createContext("/$route", requestHandler)
        }

        server.executor = null
        serverRunning = true
        server.start()

        return@makeNativeFnWithType makeNull()
    }

    envP.defineType(
        "HttpHandler",
        NetServeTypes.Handler
    )

    envP.defineType(
        "HttpRequest",
        NetServeTypes.Request
    )

    envP.defineType(
        "HttpResponse",
        NetServeTypes.Response
    )

    return envP
}

open class Variable(
    open var constant: Boolean = true,
    open val name: String,
    open var value: RuntimeVal,
    open val lock: ReentrantLock,
    open val type: Type?,
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
            throw Error("Cannot mutate ${this.name} since it is constant. Try declaring it with the 'mutable' keyword instead.")
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
    private val localTypes: HashMap<String, Type> = types,
    private val isCoroutine: Boolean = false,
) {

    /**
     * Create a deep copy of this environment.
     * @return A deep copy of this environment and its parent recursively.
     */
    fun copy(): Environment =
        Environment(
            parent?.copy(),
            variables.toHashSet(),
            localTypes,
            isCoroutine
        )

    fun getTypeOrNull(name: String): Type? {
        if (name in localTypes.keys) return localTypes[name]!!

        return this.parent?.getTypeOrNull(name)
    }

    fun defineType(name: String, type: Type) {
        localTypes[name] = type
    }

    fun removeType(name: String): Type? {
        return localTypes.remove(name)
    }

    fun declareVar(name: String, value: RuntimeVal, constant: Boolean, type: Type?, soft: Boolean = false): Variable {
        if (this.resolve(name) != null && !soft) {
            if (isCoroutine) {
                throw Error("Cannot redeclare variable $name. Try using the ScopeCopy annotation to make a copy of the outer scope.")
            }

            println(this.resolve(name)?.getVarOrNull(name)?.value?.value)

            throw Error("""
                |Cannot redeclare variable $name. Try using the mutable keyword to declare variables.
                |Previously defined as ${this.resolve(name)?.getVarOrNull(name)?.value?.toFancy()}.
            """.trimMargin())
        } else if (this.resolve(name) != null) {
            return this.getVar(name)
        }

        val v = Variable(constant, name, value, ReentrantLock(), type)

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

    public fun getVarOrNull(name: String): Variable? {
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
        localTypes,
        isCoroutine
    )
}