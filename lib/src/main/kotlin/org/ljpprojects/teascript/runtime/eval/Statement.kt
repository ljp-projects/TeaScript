@file:Suppress("ScopeFunctionConversion")

package org.ljpprojects.teascript.runtime.eval

import org.ljpprojects.teascript.globalVars
import okhttp3.*
import org.ljpprojects.teascript.frontend.*
import org.ljpprojects.teascript.runtime.Environment
import org.ljpprojects.teascript.runtime.evaluate
import org.ljpprojects.teascript.runtime.makeGlobalEnv
import org.ljpprojects.teascript.runtime.types.*
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture

fun evalProgram(program: Program, env: Environment): RuntimeVal {
    var result: RuntimeVal = makeNull()

    var stmtPointer = 0

    while (stmtPointer < program.body.size) {
        result = evaluate(program.body[stmtPointer++], env)
    }

    return result
}

fun evalVarDecl(decl: VarDecl, env: Environment): RuntimeVal {
    val value: RuntimeVal = decl.value.let {
        return@let if (it != null) evaluate(it, env) else null
    } ?: makeNull()

    val useAltered = decl.identifier.type == null && decl.modifiers.contains(Modifier(ModifierType.Annotation, "AlteredTypeInference"))

    val type =
        if (useAltered)
            getStrictestSubType(value.type!!, value).first
        else if (decl.identifier.type == null) value.type ?: AnyType()
        else typeEval(decl.identifier.type!!, env)

    require(type matches value) {
        "Expected a value of type $type for variable ${decl.identifier.symbol}, instead got ${value.type}"
    }

    return env.declareVar(decl.identifier.symbol, value, decl.constant, type).value
}

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

    evaluate(Parser(file).produceAST(), env)

    env.variables.forEach {
        if (currentEnvironment.resolve(it.name) != null && !globalVars.contains(it.name)) {
            throw RuntimeException(
                "Conflicting name of function/variable with ${it.name} of ${decl.file}. It will not be imported."
            )
        } else if (!globalVars.contains(it.name) && (decl.symbols.contains(it.name) || decl.symbols.isEmpty())) {
            currentEnvironment.declareVar(it.name, it.value, true, it.type)
        }
    }

    return `import`
}

fun evalForDecl(decl: ForDecl, env: Environment): RuntimeVal {
    val fn = object : ForValue(
        param = decl.parameter,
        declEnv = env,
        obj = evaluate(decl.obj, env) as IterableVal,
        value = decl.body,
        modifiers = decl.modifiers
    ) {}

    when (fn.obj) {
        is ObjectVal -> {
            for ((_, value) in fn.obj.value) {
                val scope = Environment(fn.declEnv)

                scope.declareVar(fn.param.symbol, value.first, true, value.second)

                for (statement in fn.value) {
                    if (fn.modifiers.any { it.type == ModifierType.Annotation && it.value == "Concurrent" }) {
                        evaluate(statement, scope)
                    } else {
                        evaluate(statement, scope)
                    }
                }
            }
        }
    }

    return fn
}

/**
 * Handle the return statement
 * @throws Return This is used to return values no matter where it is placed.
 */
fun evalReturnStatement(stmt: ReturnStatement, env: Environment): Nothing {
    throw Return(evaluate(stmt.value, env))
}