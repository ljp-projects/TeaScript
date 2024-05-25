package runtime

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.Date
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.system.exitProcess

fun makeGlobalEnv(argv: Array<StringVal>): Environment {
    val env = Environment(null)

    env.declareVar("true", makeBool(true), true)
    env.declareVar("false", makeBool(false), true)

    env.declareVar("null", makeNull(), false)

    val io: HashMap<String, RuntimeVal> = hashMapOf(
        "println" to makeNativeFn("console.log", -1, "println") { args, _ ->
            val msg = StringBuilder()

            args.forEachIndexed { index, arg ->
                if (index < args.size - 1) {
                    msg.append(arg.value).append(", ")
                } else {
                    msg.append(arg.value)
                }
            }

            println(msg.toString())

            makeNull()
        },
        "print" to makeNativeFn("console.log", -1, "print") { args, _ ->
            val msg = StringBuilder()

            args.forEachIndexed { index, arg ->
                if (index < args.size - 1) {
                    msg.append(arg.value).append(", ")
                } else {
                    msg.append(arg.value)
                }
            }

            print(msg.toString())

            return@makeNativeFn makeNull()
        },
        "eprintln" to makeNativeFn("console.error", -1, "eprintln") { args, _ ->
            val msg = StringBuilder()

            args.forEachIndexed { index, arg ->
                if (index < args.size - 1) {
                    msg.append(arg.value).append(", ")
                } else {
                    msg.append(arg.value)
                }
            }

            System.err.println(msg.toString())

            makeNull()
        },
        "eprint" to makeNativeFn("console.error", -1, "eprint") { args, _ ->
            val msg = StringBuilder()

            args.forEachIndexed { index, arg ->
                if (index < args.size - 1) {
                    msg.append(arg.value).append(", ")
                } else {
                    msg.append(arg.value)
                }
            }

            System.err.print(msg.toString())

            return@makeNativeFn makeNull()
        },
        "readln" to makeNativeFn("__std.not_supported_js") { args, _ ->
            val promise = args[0] as PromiseVal

            promise.value.get()
        },
        "exit" to makeNativeFn("__std.exit") { args, _ ->
            val code = (args[0] as NumberVal).value

            exitProcess(code.toInt())
        }
    )

    val data: HashMap<String, RuntimeVal> = hashMapOf(
        "joinPromise" to makeNativeFn("__std.not_supported_js") { args, _ ->
            val promise = args[0] as PromiseVal

            promise.value.get()
        },
        "arr" to makeNativeFn("__std.arr", -1) { args, _ ->
            val obj = makeObject(Pair(mutableListOf(), mutableListOf()))

            for (arg in args) {
                obj.value.first.addLast(obj.value.first.size.toString())
                obj.value.second.addLast(arg)
            }

            return@makeNativeFn obj
        },
        "pusharr" to makeNativeFn("__std.pusharr", 2) { args, _ ->
            val obj = args[0] as ObjectVal

            obj.value.first.addLast(obj.value.first.size.toString())
            obj.value.second.addLast(args[1])

            return@makeNativeFn makeNull()
        },
        "poparr" to makeNativeFn("__std.poparr") { args, _ ->
            val obj = args[0] as ObjectVal

            obj.value.first.removeLast()

            return@makeNativeFn obj.value.second.removeLastOrNull() ?: makeNull()
        },
        "assignArrIdx" to makeNativeFn("__std.assignArrIdx", 3) { args, _ ->
            val obj = args[0] as ObjectVal
            val idx = args[1] as NumberVal
            val new = args[2]

            obj.value.second[idx.value.toInt()] = new

            return@makeNativeFn obj.value.second.removeLastOrNull() ?: makeNull()
        }
    )

    val net: HashMap<String, RuntimeVal> = hashMapOf(
        "post" to makeNativeFn("fetch", 2) { args, _ ->
            val gson = Gson()
            val body = gson.toJson((args[1] as ObjectVal).value).toRequestBody("application/json".toMediaTypeOrNull())

            val future = CompletableFuture<RuntimeVal>()
            val client = OkHttpClient()
            val req = Request.Builder().url((args[0] as StringVal).value).post(body).build()

            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    future.completeExceptionally(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    future.complete(makeString(response.body?.string() ?: ""))
                }

            })

            return@makeNativeFn makePromise(future)
        },
        "get" to makeNativeFn("fetch") { args, _ ->
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

            return@makeNativeFn makePromise(future)
        }
    )

    val math = hashMapOf(
        "cos" to makeNativeFn("Math.cos") { args, _ ->
            return@makeNativeFn makeNumber(cos((args[0] as NumberVal).value))
        },
        "sin" to makeNativeFn("Math.sin") { args, _ ->
            return@makeNativeFn makeNumber(sin((args[0] as NumberVal).value))
        },
        "rand" to makeNativeFn("Math.random", 2) { args, _ ->
            val min = (args[0] as NumberVal).value
            val max = (args[1] as NumberVal).value

            return@makeNativeFn makeNumber(floor(Math.random() * (max - min + 1) + min))
        },
        "pi" to makeNumber(Math.PI)
    )

    env.declareVar("io", makeObject(io.keys.toMutableList() to io.values.toMutableList()), true)
    env.declareVar("net", makeObject(net.keys.toMutableList() to net.values.toMutableList()), true)
    env.declareVar("data", makeObject(data.keys.toMutableList() to data.values.toMutableList()), true)
    env.declareVar("math", makeObject(math.keys.toMutableList() to data.values.toMutableList()), true)

    env.declareVar("time", makeNativeFn("Date.now", 0) { _, _ ->
        return@makeNativeFn makeNumber(Date().time.toDouble())
    }, true)

    val args = Pair<MutableList<String>, MutableList<RuntimeVal>>(mutableListOf(), mutableListOf())

    argv.forEachIndexed { index, str ->
        args.first.addLast(index.toString())
        args.second.addLast(str)
    }

    env.declareVar("argv", makeObject(args), true)

    return env
}

class Variable(
    var constant: Boolean = true,
    val name: String,
    var value: RuntimeVal,
    val index: Int,
    private val bindings: HashSet<Variable> = hashSetOf()
) {
    companion object {
        const val DEFAULT_VAR_NAME = "+~_!)@(#*$&%^?{}?|:><[];'/.,'"
    }

    /**
     * 'Peg' a variable to another so that they both always have the same value
     */
    fun peg(variable: Variable) {
        if (this in variable.bindings) {
            throw ClassCircularityError("Cannot peg ${variable.name} to $name since ${variable.name} is pegged to $name")
        }

        bindings.add(variable)
    }

    /**
     * 'Unpeg' a variable from another so that they no longer both always have the same value
     */
    fun unpeg(variable: Variable) {
        if (this !in variable.bindings) {
            throw IllegalAccessError("Cannot unpeg ${variable.name} from $name since they are not pegged.")
        }

        bindings.remove(variable)
    }

    /**
    * THIS FUNCTION DOES NOT GUARANTEE TYPE SAFETY OF THE OLD AND ASSIGNED VALUE */
    fun mutate(new: RuntimeVal) {
        if (constant) {
            throw IllegalStateException("Cannot mutate $name since it is constant. Try declaring it with the 'mutable' keyword instead.")
        }

        value = new

        bindings.forEach {
            if (!it.constant) it.mutate(new)
        }
    }
}

class Environment(
    private val parent: Environment?,
    val variables: HashSet<Variable> = hashSetOf()
) {
    fun declareVar(name: String, value: RuntimeVal, constant: Boolean, idx: Int = -1): RuntimeVal  {
        if (resolve(name) != null) {
            throw IllegalAccessException("Cannot redeclare variable $name.")
        }

        variables.add(Variable(constant, name, value, idx))

        return value
    }

    fun getVar(name: String): Variable {
        variables.forEach { if (it.name == name) return it }

        if (parent != null) {
            return parent.getVar(name)
        }

        throw IllegalAccessException("Variable $name was never declared.")
    }

    fun getSize(): Int {
        if (parent == null) {
            return variables.size
        }

        return variables.size + parent.getSize()
    }

    fun getVarOrNull(name: String): Variable? {
        variables.forEach { if (it.name == name) return it }

        if (parent != null) {
            return parent.getVarOrNull(name)
        }

        return null
    }

    fun assignVar(name: String, value: RuntimeVal): RuntimeVal {
        resolve(name)
            ?: throw IllegalAccessException("Variable $name cannot be reassigned as it was never declared.")

        getVar(name).mutate(value)

        return value
    }

    fun lookupVar(name: String): RuntimeVal  {
        val env = resolve(name) ?: throw IllegalAccessException("Variable '$name' cannot be accessed as it was never declared. Did you mean '${findClosestVar(name)}'?")

        return env.getVarOrNull(name)?.value ?: makeNull()
    }

    private fun findClosestVar(word: String): String? {
        var closestWord: String? = null
        var minDistance = Int.MAX_VALUE

        for (w in variables.map { it.name }) {
            val distance = calculateDistance(word, w)
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

    fun resolve(name: String): Environment?  {
        return if (getVarOrNull(name) != null) {
            this
        } else if (this.parent != null) {
            parent.resolve(name)
        } else {
            null
        }
    }

    fun toReadonly(): Environment = Environment(
        parent,
        variables.map {
            val c = it
            c.constant = true
            c
        }.toHashSet()
    )
}