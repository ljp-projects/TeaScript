package runtime.eval.transpile

import frontend.AssignmentExpr
import frontend.CallExpr
import frontend.Identifier
import frontend.Program
import globalVars
import runtime.*

fun transpileAssignment(node: AssignmentExpr, env: Environment): String {
    if (node.assigned !is Identifier) {
        throw RuntimeException("Expected an identifier in an assignment expression.")
    }

    val value = evaluate(node.value, env)

    if (env.lookupVar(node.assigned.symbol).kind != value.kind) {
        throw IllegalArgumentException("Expected a value of type ${env.lookupVar(node.assigned.symbol).kind}, instead got ${value.kind}")
    }

    return "${node.assigned.symbol} = ${value.value};"
}
fun transpileIdentifier(identifier: Identifier, env: Environment): String {
    return identifier.symbol
}
fun transpileCallExpr(call: CallExpr, env: Environment): String {
    val actArgs = call.args.map { transpile(it, env) }
    val fn = evaluate(call.caller, env)

    if (fn is NativeFnValue) {
        if (fn.arity > -1 && fn.arity != actArgs.size) {
            throw RuntimeException("Native Function ${(call.caller as Identifier).symbol} expected ${fn.arity} arguments, instead got ${actArgs.size}.")
        }

        return "${fn.name}(${actArgs.joinToString()})"
    }

    if (fn is FunctionValue) {
        // An arity of -1 means any number of arguments are allowed
        if (fn.arity > -1 && fn.arity != actArgs.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${actArgs.size}.")
        }

        val scope = Environment(fn.declEnv)

        val result = StringBuilder("")
        var fr: RuntimeVal = makeNull()

        result.append(transpilePrefix(fn, env))

        fn.value.forEach { statement ->
            fr = evaluate(statement, scope)
        }

        result.append("${
            fn.name.first ?: if (call.caller !is Identifier) ("((${fn.params.joinToString { it.first }}) => {" +
                    transpileProgram(
                        object : Program(
                            "program",
                            fn.value.toMutableList()
                        ) {},
                        scope
                    ) + "})")
            else call.caller.symbol
        }(${actArgs.joinToString()})")

        result.append(transpileSuffix(fn, env))

        val type: String = if (fn.name.second != null && scope.resolve(fn.name.second!!) != null && fn.name.second !in globalVars)
            scope.lookupVar(fn.name.second!!).value.toString()
        else if (fn.name.second == null)
            "any"
        else fn.name.second!!

        if (fr.kind != type && !fn.promise && type != "any") {
            throw IllegalStateException("Expected ${fn.name.first} to return a ${type}, but actually got ${fr.kind}.")
        }

        return result.toString()
    }

    throw RuntimeException("Attempted to call a non-function value.")
}