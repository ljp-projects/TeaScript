package runtime.eval.transpile

import frontend.*
import globalVars
import okhttp3.*
import runtime.*
import runtime.types.ForValue
import runtime.types.makeAny
import runtime.types.typeEval
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull

fun transpileProgram(program: Program, env: Environment): String {
    var result = ""

    for (statement in program.body) {
        result += "\n"
        result += transpile(statement, env)
    }

    return result
}

fun transpileVarDecl(decl: VarDecl, env: Environment): String {
    val actValue: String = decl.value.getOrNull().let {
        return@let (if (it != null) transpile(it, env) else null).toString()
    }

    return """
        ${if (decl.constant) "const" else "let"} ${decl.identifier.symbol} = $actValue;
    """.trimIndent()
}
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
        if (currentEnvironment.resolve(it.name) != null && !globalVars.contains(it.name)) {
            System.err.println(
                "${it.value.kind} of ${it.value.value} (${it.name}) to ${
                    currentEnvironment.resolve(it.name)?.lookupVar(it.name)?.value
                }"
            )
            throw RuntimeException(
                "Conflicting name of function/variable with ${it.name} of ${decl.file}. It will not be imported."
            )
        } else if (!globalVars.contains(it.name) && decl.symbols.contains(it.name)) {
            currentEnvironment.declareVar(it.name, it.value, true)
        }
    }

    res.append("\n// End imports from transpiled file \"${decl.file}\"")

    return "$res"
}
fun transpileForDecl(decl: ForDecl, env: Environment): String {
    val fn = object : ForValue(
        param = decl.parameter,
        declEnv = env,
        obj = env.lookupVar(decl.obj.symbol),
        value = decl.body,
        modifiers = decl.modifiers
    ) {}

    var res = "for (const ${fn.param} in ${decl.obj}) {\n"

    val innerScope = Environment(fn.declEnv)

    innerScope.declareVar(fn.param.symbol, makeAny(if (fn.param.type == null) "any" else typeEval(fn.param.type!!, env).toString()), true)

    for (statement in fn.value) {
        res += "\t"
        res += transpile(statement, innerScope)
        res += "\n"
    }

    return "$res\n}"
}