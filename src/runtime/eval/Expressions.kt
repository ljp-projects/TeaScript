package runtime.eval

import com.google.gson.Gson
import frontend.*
import globalCoroutineScope
import globalVars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import runtime.*
import java.util.concurrent.CompletableFuture
import kotlin.math.pow

fun evalNumericBinaryExpr(lhs: NumberVal, rhs: NumberVal, op: String, env: Environment): RuntimeVal {
    return when (op) {
        "+" -> makeNumber(lhs.value + rhs.value)
        "-" -> makeNumber(lhs.value - rhs.value)
        "*" -> makeNumber(lhs.value * rhs.value)
        "/" -> if (lhs.value != 0.0 && rhs.value != 0.0) makeNumber(lhs.value / rhs.value) else throw RuntimeException("Cannot divide by zero.")
        "%" -> makeNumber(lhs.value % rhs.value)
        "^" -> makeNumber(lhs.value.pow(rhs.value))
        "|" -> makeNumber((lhs.value.toRawBits() or rhs.value.toRawBits()).toDouble())
        else -> evalOtherBinaryExpr(lhs, rhs, op, env)
    }
}

fun evalComparisonBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String, env: Environment): BoolVal {
    return when (op) {
        "is" -> makeBool(lhs.toCommon() == rhs.toCommon())
        "isnt" -> makeBool(Gson().toJson(lhs) != Gson().toJson(rhs))
        "or" -> makeBool((lhs as BoolVal).value || (rhs as BoolVal).value)
        "nor" -> makeBool(!((lhs as BoolVal).value || (rhs as BoolVal).value))
        "and" -> makeBool((lhs as BoolVal).value && (rhs as BoolVal).value)
        "nand" -> makeBool(!((lhs as BoolVal).value && (rhs as BoolVal).value))
        ">" -> makeBool((lhs as NumberVal).value > (rhs as NumberVal).value)
        "<" -> makeBool((lhs as NumberVal).value < (rhs as NumberVal).value)
        else -> throw RuntimeException("Undefined comparison operator '$op'")
    }
}

fun evalOtherBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String, env: Environment): RuntimeVal {
    return when (op) {
        "to" -> {
            val map = Pair<MutableList<String>, MutableList<RuntimeVal>>(mutableListOf(), mutableListOf())

            var inc = (lhs as NumberVal).value
            var idx = 0

            while (inc <= (rhs as NumberVal).value) {
                map.first.addLast(idx.toString())
                map.second.addLast(makeNumber(inc))

                idx++
                inc++
            }

            makeObject(map)
        }

        else -> makeNull()
    }
}

fun evalIdentBinaryExpr(lhs: Identifier, rhs: Identifier, op: String, env: Environment): RuntimeVal {
    return when (op) {
        "\\>" -> {
            env.getVar(rhs.symbol).peg(env.getVar(lhs.symbol))

            makeNull()
        }
        else -> throw RuntimeException("Undefined operator '$op'")
    }
}

fun evalStringBinaryExpr(lhs: StringVal, rhs: RuntimeVal, op: String, env: Environment): StringVal {
    return when (op) {
        "+" -> makeString(lhs.value + rhs.value)
        "-" -> {
            val a = lhs.value
            val b = (rhs as StringVal).value
            val diff = StringBuilder(a.length)

            if (b.length > a.length) {
                b.forEachIndexed { idx, char ->
                    if (a.getOrNull(idx) == char) {
                        diff.append(char)
                    }
                }
            } else {
                a.forEachIndexed { idx, char ->
                    if (b.getOrNull(idx) == char) {
                        diff.append(char)
                    }
                }
            }

            makeString(diff.toString())
        }
        else -> throw RuntimeException("Undefined string operator '$op'")
    }
}

fun evalBinaryExpr(expr: BinaryExpr, env: Environment): RuntimeVal {
    val (lhs, rhs) = arrayOf(evaluate(expr.left, env), evaluate(expr.right, env))
    val comparisonOps = hashSetOf("is", "isnt", "or", "nor", "and", "nand", ">", "<")

    return if (expr.operator in comparisonOps) {
        evalComparisonBinaryExpr(lhs, rhs, expr.operator, env)
    } else if (expr.operator == "\\>") {
        evalIdentBinaryExpr(expr.left as Identifier, expr.right as Identifier, expr.operator, env)
    } else if (lhs is NumberVal && rhs is NumberVal) {
        evalNumericBinaryExpr(lhs, rhs, expr.operator, env)
    } else if (lhs is StringVal) {
        evalStringBinaryExpr(lhs, rhs, expr.operator, env)
    } else {
        evalOtherBinaryExpr(lhs, rhs, expr.operator, env)
    }
}

fun evalMemberExpr(expr: MemberExpr, env: Environment): RuntimeVal {
    val obj = evaluate(expr.obj, env) as ObjectVal
    val prop = when (expr.prop) {
        is Identifier -> expr.prop.symbol
        is NumberLiteral -> expr.prop.value.toInt().toString()
        is StringLiteral -> expr.prop.value
        else -> throw Exception("huh")
    }

    val idx = obj.value.first.indexOf(prop)

    if (idx == -1) {
        throw IllegalAccessException("Cannot access member $prop as it is either private or doesn't exist.")
    }

    return obj.value.second[idx]
}

fun evalIdentifier(identifier: Identifier, env: Environment): RuntimeVal {
    return env.lookupVar(identifier.symbol)
}

fun evalAssignment(node: AssignmentExpr, env: Environment, file: String): RuntimeVal {
    if (node.assigned !is Identifier) {
        throw RuntimeException("Expected an identifier in an assignment expression.")
    }

    val value = evaluate(node.value, env)
    val variable = env.lookupVar(node.assigned.symbol)
    val type: String? = if (env.resolve(node.assigned.type) != null) {
        env.lookupVar(node.assigned.type).value.toString()
    } else null

    if ((type ?: variable.kind) != value.kind) {
        Error("Attempted to assign a value of ${value.kind} (${value.toFancy()}) to ${node.assigned.symbol}, which is a ${type ?: variable.kind}.", file)
            .raise()
    }

    return env.assignVar(node.assigned.symbol, value, file)
}

fun evalObjectExpr(expr: ObjectLiteral, env: Environment): RuntimeVal {
    val props = Pair<MutableList<String>, MutableList<RuntimeVal>>(mutableListOf(), mutableListOf())

    expr.properties.forEach {
        if (it.value.isEmpty) {
            props.first.addLast(it.key)
            props.second.addLast(env.lookupVar(it.key))
        } else {
            props.first.addLast(it.key)
            props.second.addLast(evaluate(it.value.get(), env))
        }
    }

    return makeObject(props)
}

fun runPrefix(fn: FunctionValue, env: Environment) {
    fn.prefixes.forEach {
        val prefix = env.lookupVar(it) as FunctionValue

        runPrefix(prefix, env)

        for (statement in prefix.value) {
            evaluate(statement, prefix.declEnv)
        }

        runSuffix(prefix, env)
    }
}

fun runSuffix(fn: FunctionValue, env: Environment) {
    fn.suffixes.forEach {
        val suffix = env.lookupVar(it) as FunctionValue

        runPrefix(suffix, env)

        for (statement in suffix.value) {
            evaluate(statement, suffix.declEnv)
        }

        runSuffix(suffix, env)
    }
}

fun evalCallExpr(call: CallExpr, env: Environment): RuntimeVal {
    val args = call.args.map { evaluate(it, env) }
    val fn = evaluate(call.caller, env)

    if (fn is NativeFnValue) {
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("Native Function ${(call.caller as Identifier).symbol} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        return fn.value(args, env)
    }

    if (fn is FunctionValue) {
        // An arity of -1 means any number of arguments are allowed
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        val scope = if (fn.mutating) Environment(fn.declEnv) else Environment(fn.declEnv).toReadonly()

        fn.params.forEachIndexed { index, pair ->
            val type: String = if (scope.resolve(pair.second) != null) {
                scope.lookupVar(pair.second).value.toString()
            } else pair.second
            
            if (type != "any" && type != args[index].kind) {
                throw IllegalArgumentException("Cannot pass argument of type ${args[index].kind} to an expected type of $type.")
            }

            scope.declareVar(pair.first, args[index], true)
        }

        var result: RuntimeVal = makeNull()

        if (fn.coroutine && !fn.promise) globalCoroutineScope.launch {
            runPrefix(fn, env)

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }

            runSuffix(fn, env)
        } else if (fn.coroutine) {
            val future = CompletableFuture<RuntimeVal>()

            globalCoroutineScope.launch {
                runPrefix(fn, env)

                for (statement in fn.value) {
                    result = evaluate(statement, scope)
                }

                runSuffix(fn, env)

                val type: String = if (fn.name.second != null && scope.resolve(fn.name.second!!) != null)
                    scope.lookupVar(fn.name.second!!).value.toString()
                else fn.name.second ?: "any"

                if (type != "any" && type != result.kind) {
                    throw IllegalArgumentException("Cannot pass argument of type $result.kind} to an expected type of $type.")
                }

                future.complete(result)
            }

            result = makePromise(future)
        } else {
            runPrefix(fn, env)

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }

            runSuffix(fn, env)
        }
                
        val type: String = if (fn.name.second != null && scope.resolve(fn.name.second!!) != null && fn.name.second !in globalVars)
            scope.lookupVar(fn.name.second!!).value.toString()
        else fn.name.second ?: "any"

        if (result.kind != type && !fn.promise) {
            throw IllegalStateException("Expected ${fn.name.first} to return a ${type}, but actually got ${result.kind}.")
        }

        return result
    }

    throw RuntimeException("Attempted to call a non-function value (${fn.kind}).")
}

fun evalAwaitDecl(decl: AwaitDecl, env: Environment): RuntimeVal {
    val fn = object : AwaitValue(
        param = decl.parameter,
        declEnv = env,
        obj = evaluate(decl.obj, env) as PromiseVal,
        value = decl.body,
        async = decl.modifiers.none { it.type == ModifierType.Synchronised }
    ) {}

    val scope = Environment(fn.declEnv)
    var result: RuntimeVal = makeNull()

    if (fn.async) {
        globalCoroutineScope.launch {
            val res = fn.obj.value.join()

            scope.declareVar(fn.param.symbol, res, true)

            for (statement in fn.value) launch {
                result = evaluate(statement, scope)
            }
        }
    } else {
        val res = fn.obj.value.join()

        scope.declareVar(fn.param.symbol, res, true)

        for (statement in fn.value) {
            result = evaluate(statement, scope)
        }
    }

    return result
}

fun evalAfterDecl(decl: AfterDecl, env: Environment): RuntimeVal = runBlocking {
    val ms = evaluate(decl.ms, env)

    val fn = makeAfter(ms, env, decl.body, !decl.modifiers.none { it.type == ModifierType.Synchronised })

    var result: RuntimeVal = makeNull()

    if (fn.async) {
        globalCoroutineScope.launch(Dispatchers.IO) {
            val scope = Environment(fn.declEnv)

            delay((fn.ms as NumberVal).value.toULong().toLong())

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }
        }
    } else {
        val scope = Environment(fn.declEnv)

        delay((fn.ms as NumberVal).value.toULong().toLong())

        for (statement in fn.value) {
            result = evaluate(statement, scope)
        }
    }

    return@runBlocking result
}

fun evalIfDecl(decl: IfDecl, env: Environment): RuntimeVal {
    val cond = evaluate(decl.cond, env)
    val fn = object : IfValue(
        cond = cond,
        declEnv = env,
        value = decl.body,
        modifiers = decl.modifiers,
        otherwise = decl.otherwise,
        orStmts = decl.or
    ) {}

    var result: RuntimeVal = makeNull()

    if (fn.async) {
        globalCoroutineScope.launch {
            if ((fn.cond as BoolVal).value) {
                val scope = Environment(fn.declEnv)

                for (statement in fn.value) {
                    result = evaluate(statement, scope)
                }
            } else if (fn.orStmts.size > 0) {
                var didRun = false

                for (or in fn.orStmts) {
                    if ((evaluate(or.cond, env) as BoolVal).value) {
                        val scope = Environment(fn.declEnv)

                        for (statement in or.body) {
                            result = evaluate(statement, scope)
                        }

                        didRun = true

                        break
                    }

                }

                if (!didRun && fn.otherwise != null) {
                    val scope = Environment(fn.declEnv)

                    for (statement in fn.otherwise) {
                        result = evaluate(statement, scope)
                    }
                }
            } else if (fn.otherwise != null) {
                val scope = Environment(fn.declEnv)

                for (statement in fn.otherwise) {
                    result = evaluate(statement, scope)
                }
            }
        }
    } else {
        if ((fn.cond as BoolVal).value) {
            val scope = Environment(fn.declEnv)

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }
        } else if (fn.orStmts.size > 0) {
            var didRun = false

            for (or in fn.orStmts) {
                if ((evaluate(or.cond, env) as BoolVal).value) {
                    val scope = Environment(fn.declEnv)

                    for (statement in or.body) {
                        result = evaluate(statement, scope)
                    }

                    didRun = true

                    break
                }
            }

            if (!didRun && fn.otherwise != null) {
                val scope = Environment(fn.declEnv)

                for (statement in fn.otherwise) {
                    result = evaluate(statement, scope)
                }
            }
        } else if (fn.otherwise != null) {
            val scope = Environment(fn.declEnv)

            for (statement in fn.otherwise) {
                result = evaluate(statement, scope)
            }
        }
    }

    return result
}

fun evalFuncDecl(decl: FunctionDecl, env: Environment): RuntimeVal {
    val fn = object : FunctionValue(
        name = decl.name?.symbol to decl.name?.type,
        declEnv = env,
        params = decl.parameters,
        value = decl.body,
        arity = decl.arity,
        modifiers = decl.modifiers,
    ) {}

    // The function is not a lambda
    if (fn.name.first != null) {
        env.declareVar(fn.name.first!!, fn, true)
    }

    return fn
}

fun evalClassDecl(decl: ClassDecl, env: Environment): RuntimeVal {
    val c = object : ClassValue(
        kind = decl.name.symbol,
        name = decl.name.symbol to decl.name.type,
        declEnv = env,
        params = decl.parameters,
        value = decl.body,
        arity = decl.arity
    ) {}

    val scope = Environment(c.declEnv)

    for (statement in c.value) {
        evaluate(statement, scope)
    }

    val pv = mutableMapOf<String, RuntimeVal>()

    scope.variables.map {
        if (it.value is FunctionValue) {
            if (!(it.value as FunctionValue).private) pv[it.name] = it.value
        } else {
            pv[it.name] = it.value
        }
    }

    val public = pv.keys.toMutableList() to pv.values.toMutableList()

    val init = makeNativeFn(c.name.first, c.arity) { args, _ ->
        for (index in 0..<c.params.size) {
            val pair = c.params.toList()[index]

            val type: String = if (scope.resolve(pair.second) != null) {
                scope.lookupVar(pair.second).value.toString()
            } else pair.second

            if (type != "any" && type != args[index].kind) {
                throw IllegalArgumentException("Cannot pass argument of type ${args[index].kind} to an expected type of $type.")
            }

            scope.declareVar(c.params.toList()[index].first, args.getOrNull(index) ?: makeAny(c.params.toList()[index].second), c.params.toList()[index].first.uppercase() == c.params.toList()[index].first)
        }

        makeObject(public, c.name.first)
    }

    env.declareVar(decl.name.symbol, init, true)

    return init
}