@file:Suppress("ScopeFunctionConversion")

package runtime.eval

import frontend.*
import globalCoroutineScope
import globalVars
import kotlinx.coroutines.launch
import okhttp3.*
import runtime.Environment
import runtime.evaluate
import runtime.makeGlobalEnv
import runtime.types.*
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
fun evalProgram(program: Program, env: Environment): RuntimeVal {
    var result: RuntimeVal = makeNull()

    var stmtPointer = 0

    while (stmtPointer < program.body.size) {
        result = evaluate(program.body[stmtPointer++], env)
    }

    return result
}

@OptIn(ExperimentalTime::class)
fun evalVarDecl(decl: VarDecl, env: Environment): RuntimeVal {
    val value: RuntimeVal = decl.value.let {
        return@let if (it != null) evaluate(it, env) else null
    } ?: makeNull()

    val type = if (decl.identifier.type == null) AnyType() else typeEval(decl.identifier.type!!, env)

    require(decl.value != null && type matches value) {
        "Expected a value of type $type, instead got ${value.kind}"
    }

    return env.declareVar(decl.identifier.symbol, value, decl.constant).value
}

@OptIn(ExperimentalTime::class)
fun evalImportDecl(decl: ImportDecl, currentEnvironment: Environment): RuntimeVal {
    val env = makeGlobalEnv(emptyArray())

    val `import` = object : ImportVal(
        value = env,
        imports = decl.symbols
    ) {}

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
            throw RuntimeException(
                "Conflicting name of function/variable with ${it.name} of ${decl.file}. It will not be imported."
            )
        } else if (!globalVars.contains(it.name) && (decl.symbols.contains(it.name) || decl.symbols.isEmpty())) {
            currentEnvironment.declareVar(it.name, it.value, true)
        }
    }

    return `import`
}

@OptIn(ExperimentalTime::class)
fun evalForDecl(decl: ForDecl, env: Environment): RuntimeVal {
    val fn = object : ForValue(
        param = decl.parameter,
        declEnv = env,
        obj = evaluate(decl.obj, env),
        value = decl.body,
        modifiers = decl.modifiers
    ) {}

    if (fn.async) {
       for ((_, value) in (fn.obj as ObjectVal).value) {
           globalCoroutineScope.launch {
               val scope = Environment(fn.declEnv)

               scope.declareVar(fn.param.symbol, value.first, true)

               for (statement in fn.value) {
                   evaluate(statement, scope)
               }
           }
       }
    } else {
        for ((_, value) in (fn.obj as ObjectVal).value) {
            val scope = Environment(fn.declEnv)

            scope.declareVar(fn.param.symbol, value.first, true)

            for (statement in fn.value) {
                evaluate(statement, scope)
            }
        }
    }

    return fn
}