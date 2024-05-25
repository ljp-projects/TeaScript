package runtime.eval

import frontend.*
import globalCoroutineScope
import globalVars
<<<<<<< HEAD
import kotlinx.coroutines.*
=======
<<<<<<< HEAD
import kotlinx.coroutines.launch
=======
import kotlinx.coroutines.*
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
import okhttp3.*
import runtime.*
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull
<<<<<<< HEAD
=======
<<<<<<< HEAD
import kotlin.time.measureTime
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)

fun evalProgram(program: Program, env: Environment): RuntimeVal {
    var result: RuntimeVal = makeNull()

<<<<<<< HEAD
=======
<<<<<<< HEAD
    val time = measureTime {
        /*for (statement in program.body) {
            result = evaluate(statement, env)
        }*/

        program.body.forEach {
            result = evaluate(it, env)
        }
    }

    println()
    println("------------------------------")
    println("Executed code in $time")
    println("------------------------------")
    println()
=======
>>>>>>> 0279ede (This is a nightmare)
    for (statement in program.body) {
        result = evaluate(statement, env)
    }

    return result
}

fun transpileProgram(program: Program, env: Environment): String {
    var result = ""

    for (statement in program.body) {
        result += "\n"
        result += transpile(statement, env)
    }
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)

    return result
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileVarDecl(decl: VarDecl, env: Environment): String {
    val value: RuntimeVal = decl.value.getOrNull().let {
        return@let if (it != null) evaluate(it, env) else null
    } ?: makeNull()

    val actValue: String = decl.value.getOrNull().let {
        return@let if (it != null) transpile(it, env) else null
    } ?: ""

    if (decl.value.isPresent && value.kind != decl.identifier.type && decl.identifier.type != "any") {
        throw IllegalArgumentException("Expected a value of type ${decl.identifier.type}, instead got ${value.kind}")
    }

    env.declareVar(decl.identifier.symbol, value, decl.constant)

    return """
        ${if (decl.constant) "const" else "let"} ${decl.identifier.symbol} = $actValue;
    """.trimIndent()
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalVarDecl(decl: VarDecl, env: Environment): RuntimeVal {
    val value: RuntimeVal = decl.value.getOrNull().let {
        return@let if (it != null) evaluate(it, env) else null
    } ?: makeNull()

    val type: String = if (env.resolve(decl.identifier.type) != null) {
        env.lookupVar(decl.identifier.type).value.toString()
    } else if (decl.identifier.type == "infer") {
        value.kind
    } else decl.identifier.type

    if (decl.value.isPresent && value.kind != type && type != "any") {
        throw IllegalArgumentException("Expected a value of type $type, instead got ${value.kind}")
    }

    return env.declareVar(decl.identifier.symbol, value, decl.constant)
}

fun evalImportDecl(decl: ImportDecl, currentEnvironment: Environment): RuntimeVal {
    val env = makeGlobalEnv(emptyArray())
    val file = if (decl.net) {
        val future = CompletableFuture<String>()
        val client = OkHttpClient()
        val req = Request.Builder().url(decl.file).get().build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                future.complete(response.body?.string() ?: "")
            }
        })

        future.get()
    } else String(File(decl.file).readBytes())

    evaluate(Parser().produceAST(file), env)

    env.variables.forEach {
<<<<<<< HEAD
=======
<<<<<<< HEAD
        if (currentEnvironment.resolve(it.name) != null && !globalVars.contains(it.name)) {
            throw RuntimeException("Conflicting name of function/variable with ${it.name} of ${decl.file}. It will not be imported.")
        } else if (!globalVars.contains(it.name) && decl.symbols.contains(it.name)) {
            currentEnvironment.declareVar(it.name, it.value, true)
=======
>>>>>>> 0279ede (This is a nightmare)
        if (currentEnvironment.resolve(it.key) != null && !globalVars.contains(it.key)) {
            throw RuntimeException("Conflicting name of function/variable with ${it.key} of ${decl.file}. It will not be imported.")
        } else if (!globalVars.contains(it.key) && decl.symbols.contains(it.key)) {
            currentEnvironment.declareVar(it.key, it.value, true)
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
        }
    }

    return object : ImportVal(
        value = env,
        imports = decl.symbols
    ) {}
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileImportDecl(decl: ImportDecl, currentEnvironment: Environment): String {
    val env = makeGlobalEnv(emptyArray())
    val file = if (decl.net) {
        val future = CompletableFuture<String>()
        val client = OkHttpClient()
        val req = Request.Builder().url(decl.file).get().build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                future.complete(response.body?.string() ?: "")
            }
        })

        future.get()
    } else String(File(decl.file).readBytes())

    val res = StringBuilder("// Start imports from transpiled file \"${decl.file}\"")

    res.append(transpile(Parser().produceAST(file), env))

    env.variables.forEach {
        if (currentEnvironment.resolve(it.key) != null && !globalVars.contains(it.key)) {
            System.err.println("${it.value.kind} of ${it.value.value} (${it.key}) to ${currentEnvironment.resolve(it.key)?.lookupVar(it.key)?.value}")
            throw RuntimeException("Conflicting name of function/variable with ${it.key} of ${decl.file}. It will not be imported.")
        } else if (!globalVars.contains(it.key) && decl.symbols.contains(it.key)) {
            currentEnvironment.declareVar(it.key, it.value, true)
        }
    }

    res.append("\n// End imports from transpiled file \"${decl.file}\"")

    return res.toString()
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalForDecl(decl: ForDecl, env: Environment): RuntimeVal {
    val fn = object : ForValue(
        param = decl.parameter,
        declEnv = env,
        obj = env.lookupVar(decl.obj.symbol),
        value = decl.body,
        async = decl.async
    ) {}

    if (fn.async) {
        globalCoroutineScope.launch {
            (fn.obj as ObjectVal).value.first.forEachIndexed { idx, _ ->
                val scope = Environment(fn.declEnv)

                scope.declareVar(fn.param.symbol, fn.obj.value.second[idx], true)

                for (statement in fn.value) {
                    evaluate(statement, scope)
                }
            }
        }
    } else {
        (fn.obj as ObjectVal).value.first.forEachIndexed { idx, _ ->
            val scope = Environment(fn.declEnv)

            scope.declareVar(fn.param.symbol, fn.obj.value.second[idx], true)

            for (statement in fn.value) {
                evaluate(statement, scope)
            }
        }
    }

    return fn
<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
}

fun transpileForDecl(decl: ForDecl, env: Environment): String {
    val fn = object : ForValue(
        param = decl.parameter,
        declEnv = env,
        obj = env.lookupVar(decl.obj.symbol),
        value = decl.body,
        async = decl.async
    ) {}

    var res = "for (const ${fn.param} in ${decl.obj}) {\n"

    val innerScope = Environment(fn.declEnv)

    innerScope.declareVar(fn.param.symbol, makeAny(fn.param.type), true)

    for (statement in fn.value) {
        res += "\t"
        res += transpile(statement, innerScope)
        res += "\n"
    }

    return "$res\n}"
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
}