package runtime.eval

import frontend.*
import globalCoroutineScope
import globalVars
import kotlinx.coroutines.launch
import okhttp3.*
import runtime.*
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull
import kotlin.time.measureTime

fun evalProgram(program: Program, env: Environment): RuntimeVal {
    var result: RuntimeVal = makeNull()

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

    return result
}

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
        if (currentEnvironment.resolve(it.name) != null && !globalVars.contains(it.name)) {
            throw RuntimeException("Conflicting name of function/variable with ${it.name} of ${decl.file}. It will not be imported.")
        } else if (!globalVars.contains(it.name) && decl.symbols.contains(it.name)) {
            currentEnvironment.declareVar(it.name, it.value, true)
        }
    }

    return object : ImportVal(
        value = env,
        imports = decl.symbols
    ) {}
}

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
}