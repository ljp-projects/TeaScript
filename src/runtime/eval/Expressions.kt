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

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileNumericBinaryExpr(lhs: NumberVal, rhs: NumberVal, op: String, env: Environment): String {
    return when (op) {
        "+" -> "${lhs.value} + ${rhs.value}"
        "-" -> "${lhs.value} - ${rhs.value}"
        "*" -> "${lhs.value} * ${rhs.value}"
        "%" -> "${lhs.value} % ${rhs.value}"
        "|" -> "${lhs.value} | ${rhs.value}"
        "/" -> "${lhs.value} / ${rhs.value}"
        "^" -> "${lhs.value} ** ${rhs.value}"
        else -> transpileOtherBinaryExpr(lhs, rhs, op, env)
    }
}

fun transpileComparisonBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String, env: Environment): String {
    return when (op) {
        "is" -> "${Gson().toJson(lhs) == Gson().toJson(rhs)}"
        "isnt" -> "${Gson().toJson(lhs) != Gson().toJson(rhs)}"
        "or" -> "${makeBool((lhs as BoolVal).value || (rhs as BoolVal).value)}"
        "nor" -> "${makeBool(!((lhs as BoolVal).value || (rhs as BoolVal).value))}"
        "and" -> "${(lhs as BoolVal).value && (rhs as BoolVal).value}"
        "nand" -> "${!((lhs as BoolVal).value && (rhs as BoolVal).value)}"
        ">" -> "${(lhs as NumberVal).value > (rhs as NumberVal).value}"
        "<" -> "${(lhs as NumberVal).value < (rhs as NumberVal).value}"
        else -> throw RuntimeException("Undefined comparison operator '$op'")
    }
}

fun transpileOtherBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String, env: Environment): String {
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

            "[${
                map.second.joinToString(",") {
                    (it as NumberVal).value.toString()
                }
            }]"
        }
        else -> throw RuntimeException("Undefined operator '$op'")
    }
}

fun transpileStringBinaryExpr(lhs: StringVal, rhs: RuntimeVal, op: String, env: Environment): String {
    return when (op) {
        "+" -> lhs.value + rhs.value
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

            diff.toString()
        }
        else -> throw RuntimeException("Undefined string operator '$op'")
    }
}

fun transpileBinaryExpr(expr: BinaryExpr, env: Environment): String {
    val (lhs, rhs) = arrayOf(evaluate(expr.left, env), evaluate(expr.right, env))
    val comparisonOps = hashSetOf("is", "isnt", "or", "nor", "and", "nand", ">", "<")

    return if (expr.operator in comparisonOps) {
        transpileComparisonBinaryExpr(lhs, rhs, expr.operator, env)
    } else if (expr.left is Identifier && expr.right is Identifier) {
        "${expr.left.symbol} ${if (expr.operator == "^") "**" else expr.operator} ${expr.right.symbol }"
    } else if ((lhs is NumberVal && rhs is NumberVal) || (lhs.kind == "number" && rhs.kind == "number")) {
        transpileNumericBinaryExpr(makeNumber(lhs.value as Double), makeNumber(rhs.value as Double), expr.operator, env)
    } else if (lhs is StringVal) {
        transpileStringBinaryExpr(lhs, rhs, expr.operator, env)
    } else {
        transpileOtherBinaryExpr(lhs, rhs, expr.operator, env)
    }
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalComparisonBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String, env: Environment): BoolVal {
    return when (op) {
        "is" -> makeBool(Gson().toJson(lhs) == Gson().toJson(rhs))
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
<<<<<<< HEAD
=======
<<<<<<< HEAD
        else -> makeNull()
    }
}

fun evalIdentBinaryExpr(lhs: Identifier, rhs: Identifier, op: String, env: Environment): RuntimeVal {
    return when (op) {
        "\\>" -> {
            env.getVar(rhs.symbol).peg(env.getVar(lhs.symbol))

            makeNull()
        }
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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
<<<<<<< HEAD
=======
<<<<<<< HEAD
    } else if (expr.operator == "\\>") {
        evalIdentBinaryExpr(expr.left as Identifier, expr.right as Identifier, expr.operator, env)
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileMemberExpr(expr: MemberExpr, env: Environment): String {
    val obj = evaluate(expr.obj, env) as ObjectVal
    val prop = when (expr.prop) {
        is Identifier -> expr.prop.symbol
        is NumberLiteral -> expr.prop.value.toInt().toString()
        is StringLiteral -> expr.prop.value
        else -> throw Exception("Unsupported value given to member expression")
    }

    val idx = obj.value.first.indexOf(prop)

    if (idx == -1) {
        throw IllegalAccessException("Cannot access member $prop as it is either private or doesn't exist.")
    }

    return "${obj.value.second[idx].value}"
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalIdentifier(identifier: Identifier, env: Environment): RuntimeVal {
    return env.lookupVar(identifier.symbol)
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileIdentifier(identifier: Identifier, env: Environment): String {
    return identifier.symbol
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalAssignment(node: AssignmentExpr, env: Environment): RuntimeVal {
    if (node.assigned !is Identifier) {
        throw RuntimeException("Expected an identifier in an assignment expression.")
    }

    val value = evaluate(node.value, env)
    val variable = env.lookupVar(node.assigned.symbol)
    val type: String? = if (env.resolve(node.assigned.type) != null) {
        env.lookupVar(node.assigned.type).value.toString()
    } else null

    if ((type ?: variable.kind) != value.kind) {
        throw IllegalArgumentException("Expected a value of type ${(type ?: variable.kind)}, instead got ${value.kind}")
    }

    return env.assignVar(node.assigned.symbol, value)
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileObjectExpr(expr: ObjectLiteral, env: Environment): String {
    val res = StringBuilder("{")

    expr.properties.forEach {
        if (it.value.isEmpty) {
            res.append("${it.key}: ${env.lookupVar(it.key)},")
        } else {
            res.append("${it.key}: ${transpile(it.value.get(), env)},")
        }
    }

    res.append("}")

    return res.toString()
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun runPrefix(fn: FunctionValue, env: Environment) {
    if (fn.prefix != null) {
        val prefix = env.lookupVar(fn.prefix) as FunctionValue

        if (prefix.prefix != null) {
            runPrefix(prefix, env)
        }

        for (statement in prefix.value) {
            evaluate(statement, prefix.declEnv)
        }

        if (prefix.suffix != null) {
            runSuffix(prefix, env)
        }
    }
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpilePrefix(fn: FunctionValue, env: Environment): String {
    val res = StringBuilder("")

    if (fn.prefix != null) {
        val prefix = env.lookupVar(fn.prefix) as FunctionValue

        if (prefix.prefix != null) {
            res.append(transpilePrefix(prefix, env))
        }

        res.append("${prefix.name.first}();")

        if (prefix.suffix != null) {
            res.append(transpileSuffix(prefix, env))
        }
    }

    return res.toString()
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun runSuffix(fn: FunctionValue, env: Environment) {
    if (fn.suffix != null) {
        val suffix = env.lookupVar(fn.suffix) as FunctionValue

        if (suffix.prefix != null) {
            runPrefix(suffix, env)
        }

        for (statement in suffix.value) {
            evaluate(statement, suffix.declEnv)
        }

        if (suffix.suffix != null) {
            runSuffix(suffix, env)
        }
    }
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileSuffix(fn: FunctionValue, env: Environment): String {
    val res = StringBuilder("")

    if (fn.suffix != null) {
        val suffix = env.lookupVar(fn.suffix) as FunctionValue

        if (suffix.prefix != null) {
            res.append(transpilePrefix(suffix, env))
        }

        res.append("${suffix.name.first}();")

        if (suffix.suffix != null) {
            res.append(transpileSuffix(suffix, env))
        }
    }

    return res.toString()
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
        val scope = Environment(fn.declEnv)
=======
<<<<<<< HEAD
        val scope = if (fn.mutating) Environment(fn.declEnv) else Environment(fn.declEnv).toReadonly()
=======
        val scope = Environment(fn.declEnv)
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)

        fn.params.forEachIndexed { index, pair ->
            val type: String = if (scope.resolve(pair.second) != null) {
                scope.lookupVar(pair.second).value.toString()
            } else pair.second

<<<<<<< HEAD
            if (pair.second != "any" && type != args[index].kind) {
=======
<<<<<<< HEAD
            if (type != "any" && type != args[index].kind) {
=======
            if (pair.second != "any" && type != args[index].kind) {
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
                if (result.kind != fn.name.second) {
                    future.completeExceptionally(IllegalStateException("Expected ${fn.name.first} to return a ${fn.name.second}, but actually got ${result.kind}."))
=======
<<<<<<< HEAD
                val type: String = if (fn.name.second != null && scope.resolve(fn.name.second!!) != null)
                    scope.lookupVar(fn.name.second!!).value.toString()
                else if (fn.name.second == null)
                    "any"
                else fn.name.second!!

                if (type != "any" && type != result.kind) {
                    throw IllegalArgumentException("Cannot pass argument of type $result.kind} to an expected type of $type.")
                }

                if (result.kind != type) {
                    future.completeExceptionally(IllegalStateException("Expected ${fn.name.first} to return a $type, but actually got ${result.kind}."))
=======
                if (result.kind != fn.name.second) {
                    future.completeExceptionally(IllegalStateException("Expected ${fn.name.first} to return a ${fn.name.second}, but actually got ${result.kind}."))
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
=======
<<<<<<< HEAD
        val type: String = if (fn.name.second != null && scope.resolve(fn.name.second!!) != null && fn.name.second !in globalVars)
            scope.lookupVar(fn.name.second!!).value.toString()
        else if (fn.name.second == null)
            "any"
        else fn.name.second!!

        if (result.kind != type && !fn.promise && type != "any") {
=======
>>>>>>> 0279ede (This is a nightmare)
        val type: String = if (scope.resolve(fn.name.second) != null && fn.name.second !in globalVars) {
            scope.lookupVar(fn.name.second).value.toString()
        } else fn.name.second

        if (result.kind != type && !fn.promise) {
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
            throw IllegalStateException("Expected ${fn.name.first} to return a ${type}, but actually got ${result.kind}.")
        }

        return result
    }

    throw RuntimeException("Attempted to call a non-function value.")
}

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileCallExpr(call: CallExpr, env: Environment): String {
    val args = call.args.map { evaluate(it, env) }
    val actArgs = call.args.map { transpile(it, env) }
    val fn = evaluate(call.caller, env)

    if (fn is NativeFnValue) {
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("Native Function ${(call.caller as Identifier).symbol} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        return "${fn.name}(${actArgs.joinToString()})"
    }

    if (fn is FunctionValue) {
        // An arity of -1 means any number of arguments are allowed
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        val scope = Environment(fn.declEnv)

        fn.params.forEachIndexed { index, pair ->
            if (pair.second != "any" && pair.second != args[index].kind) {
                throw IllegalArgumentException("Cannot pass argument of type ${args[index].kind} to an expected type of ${pair.second}.")
            }

            scope.declareVar(pair.first, args[index], true)
        }

        val result = StringBuilder("")
        var fr: RuntimeVal = makeNull()

        result.append(transpilePrefix(fn, env))

        fn.value.forEach { statement ->
            fr = evaluate(statement, scope)
        }

        result.append("${fn.name.first}(${actArgs.joinToString()})")

        result.append(transpileSuffix(fn, env))

        if (fr.kind != fn.name.second) {
            throw IllegalStateException("Expected ${fn.name.first} to return a ${fn.name.second}, but actually got ${fr.kind}.")
        }

        return result.toString()
    }

    throw RuntimeException("Attempted to call a non-function value.")
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalAwaitDecl(decl: AwaitDecl, env: Environment): RuntimeVal {
    val fn = object : AwaitValue(
        param = decl.parameter,
        declEnv = env,
        obj = evaluate(decl.obj, env) as PromiseVal,
        value = decl.body,
        async = decl.async
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

    val fn = makeAfter(ms, env, decl.body, !decl.async)

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

<<<<<<< HEAD
=======
<<<<<<< HEAD
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileAfterDecl(decl: AfterDecl, env: Environment): String {
    val actMs = transpile(decl.ms, env)
    val ms = evaluate(decl.ms, env)

    val fn = makeAfter(ms, env, decl.body, !decl.async)

    val result = StringBuilder("setTimeout(() => {\n")

    val scope = Environment(fn.declEnv)

    for (statement in fn.value) {
        result.append("\t")
        result.append(transpile(statement, scope))
        result.append("\n")
    }

    result.append("\n}, $actMs)")

    return result.toString()
}

<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
fun evalIfDecl(decl: IfDecl, env: Environment): RuntimeVal {
    val cond = evaluate(decl.cond, env)
    val fn = object : IfValue(
        cond = cond,
        declEnv = env,
        value = decl.body,
        async = decl.async,
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
<<<<<<< HEAD
=======
<<<<<<< HEAD
                }

                if (!didRun && fn.otherwise != null) {
                    val scope = Environment(fn.declEnv)

                    for (statement in fn.otherwise) {
                        result = evaluate(statement, scope)
=======
>>>>>>> 0279ede (This is a nightmare)

                    if (!didRun && fn.otherwise != null) {
                        val scope = Environment(fn.declEnv)

                        for (statement in fn.otherwise) {
                            result = evaluate(statement, scope)
                        }
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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
<<<<<<< HEAD
            var didRun = false

=======
<<<<<<< HEAD
=======
            var didRun = false

>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
            for (or in fn.orStmts) {
                if ((evaluate(or.cond, env) as BoolVal).value) {
                    val scope = Environment(fn.declEnv)

                    for (statement in or.body) {
                        result = evaluate(statement, scope)
                    }

<<<<<<< HEAD
=======
<<<<<<< HEAD
                    break
                }
=======
>>>>>>> 0279ede (This is a nightmare)
                    didRun = true

                    break
                }

                if (!didRun && fn.otherwise != null) {
                    val scope = Environment(fn.declEnv)

                    for (statement in fn.otherwise) {
                        result = evaluate(statement, scope)
                    }
                }
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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

<<<<<<< HEAD
=======
<<<<<<< HEAD
fun evalFuncDecl(decl: FunctionDecl, env: Environment): RuntimeVal {
    val fn = object : FunctionValue(
        name = decl.name?.symbol to decl.name?.type,
=======
>>>>>>> 0279ede (This is a nightmare)
fun transpileIfDecl(decl: IfDecl, env: Environment): String {
    val actCond = transpile(decl.cond, env)
    val cond = evaluate(decl.cond, env)

    val fn = object : IfValue(
        cond = cond,
        declEnv = env,
        value = decl.body,
        async = decl.async,
        otherwise = decl.otherwise,
        orStmts = decl.or
    ) {}

    val result = StringBuilder("if($actCond) {\n")
    val scope = Environment(fn.declEnv)

    for (statement in fn.value) {
        result.append("\t")
        result.append(transpile(statement, scope))
        result.append("\n")
    }

    result.append("}")

    for (or in fn.orStmts) {
        result.append(" else if(${transpile(or.cond, env)}) {\n")

        for (statement in or.body) {
            result.append("\t")
            result.append(transpile(statement, scope))
            result.append("\n")
        }

        result.append("}")
    }

    if (fn.otherwise != null) {
        result.append(" else {\n")

        for (statement in fn.otherwise) {
            result.append("\t")
            result.append(transpile(statement, scope))
            result.append("\n")
        }

        result.append("}\n")
    }

    return result.toString()
}

fun evalFuncDecl(decl: FunctionDecl, env: Environment): RuntimeVal {
    val fn = object : FunctionValue(
        name = decl.name.symbol to decl.name.type,
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
        declEnv = env,
        params = decl.parameters,
        value = decl.body,
        coroutine = decl.coroutine,
        prefix = decl.prefix,
        suffix = decl.suffix,
        arity = decl.arity,
        private = decl.private,
<<<<<<< HEAD
=======
<<<<<<< HEAD
        promise = decl.promise,
        mutating = decl.mutating,
        static = decl.static
    ) {}

    // The function is not a lambda
    if (decl.name != null) {
        env.declareVar(decl.name.symbol, fn, true)
    }

    return fn
=======
>>>>>>> 0279ede (This is a nightmare)
        promise = decl.promise
    ) {}

    env.declareVar(decl.name.symbol, fn, true)

    return fn
}

fun transpileFuncDecl(decl: FunctionDecl, env: Environment): String {
    val fn = object : FunctionValue(
        name = decl.name.symbol to decl.name.type,
        declEnv = env,
        params = decl.parameters,
        value = decl.body,
        coroutine = decl.coroutine,
        prefix = decl.prefix,
        suffix = decl.suffix,
        arity = decl.arity,
        private = decl.private,
        promise = decl.promise
    ) {}

    if (env.resolve(decl.name.symbol) == null) {
        env.declareVar(decl.name.symbol, fn, true)
    }

    val fnScope = Environment(fn.declEnv)

    fn.params.forEach {
        fnScope.declareVar(it.first, makeAny(it.second), true)
    }

    val res = StringBuilder("function ${fn.name.first}(${fn.params.joinToString { it.first }}) {\n")

    fn.value.forEachIndexed { index, statement ->
        res.append("\t")

        if (index == fn.value.size - 1) {
            res.append("return ")
        }

        res.append(transpile(statement, fnScope))
        res.append("\n")
    }

    res.append("}")

    return res.toString()
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
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
<<<<<<< HEAD
            if (!(it.value as FunctionValue).private) pv[it.key] = it.value
        } else {
            pv[it.key] = it.value
=======
<<<<<<< HEAD
            if (!(it.value as FunctionValue).private) pv[it.name] = it.value
        } else {
            pv[it.name] = it.value
=======
            if (!(it.value as FunctionValue).private) pv[it.key] = it.value
        } else {
            pv[it.key] = it.value
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
        }
    }

    val public = pv.keys.toMutableList() to pv.values.toMutableList()

    val init = makeNativeFn(c.name.first, c.arity) { args, _ ->
<<<<<<< HEAD
=======
<<<<<<< HEAD
        for (index in 0..<c.params.size) {
            val pair = c.params.toList()[index]

            val type: String = if (scope.resolve(pair.second) != null) {
                scope.lookupVar(pair.second).value.toString()
            } else pair.second

            if (type != "any" && type != args[index].kind) {
                throw IllegalArgumentException("Cannot pass argument of type ${args[index].kind} to an expected type of $type.")
            }

            scope.declareVar(c.params.toList()[index].first, args.getOrNull(index) ?: makeAny(c.params.toList()[index].second), c.params.toList()[index].first.uppercase() == c.params.toList()[index].first)
=======
>>>>>>> 0279ede (This is a nightmare)
        for (i in 0..<c.params.size) {
            if (c.params.toList().getOrNull(i)?.second != args.getOrNull(i)?.kind && i < c.arity) {
                throw IllegalArgumentException("${c.name.first}'s initialiser expected ${c.params.toList().getOrNull(i)?.second}, got ${args.getOrNull(i)?.kind}.")
            }

            scope.declareVar(c.params.toList()[i].first, args.getOrNull(i) ?: makeAny(c.params.toList()[i].second), c.params.toList()[i].first.uppercase() == c.params.toList()[i].first)
<<<<<<< HEAD
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
        }

        makeObject(public, c.name.first)
    }

    env.declareVar(decl.name.symbol, init, true)

    return init
}