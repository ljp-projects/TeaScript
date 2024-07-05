package runtime.eval.transpile

import frontend.AssignmentExpr
import frontend.CallExpr
import frontend.Identifier
import frontend.Program
import runtime.Environment
import runtime.evaluate
import runtime.transpile
import runtime.types.FunctionValue
import runtime.types.NativeFnValue
import runtime.types.RuntimeVal
import runtime.types.makeNull
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun transpileAssignment(node: AssignmentExpr, env: Environment): String {
    if (node.assigned !is Identifier) {
        throw RuntimeException("Expected an identifier in an assignment expression.")
    }

    val value = evaluate(node.value, env)

    require(env.lookupVar(node.assigned.symbol).kind == value.kind) {
        "Expected a value of type ${env.lookupVar(node.assigned.symbol).kind}, instead got ${value.kind}"
    }

    return "${node.assigned.symbol} = ${value.value};"
}
fun transpileIdentifier(identifier: Identifier, env: Environment): String = identifier.symbol

@OptIn(ExperimentalTime::class)
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
        if (fn.arity > -1 && fn.arity.toInt() != actArgs.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${actArgs.size}.")
        }

        val scope = Environment(fn.declEnv)

        val result = StringBuilder("")
        var fr: RuntimeVal = makeNull()

        fn.value.forEach { statement ->
            fr = evaluate(statement, scope)
        }

        result.append("${
            fn.name.first ?: if (call.caller !is Identifier) ("((${fn.params.keys.sortedBy { it.second }.joinToString { it.first }}) => {" +
                    transpileProgram(
                        object : Program(
                            "program",
                            fn.value.toMutableList()
                        ) {},
                        scope
                    ) + "})")
            else call.caller.symbol
        }(${actArgs.joinToString()})")

        val type = fn.name.second

        check(type matches fr) { "Expected ${fn.name.first} to return a $type, but actually got ${fr.kind}." }

        return "$result"
    }

    throw RuntimeException("Attempted to call a non-function value.")
}