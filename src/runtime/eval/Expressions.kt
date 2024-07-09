package runtime.eval

import com.google.gson.Gson
import errors.Error
import errors.IncorrectTypeError
import errors.Warning
import frontend.*
import globalCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import runtime.Environment
import runtime.evaluate
import runtime.makeGlobalEnv
import runtime.types.*
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.math.pow
import kotlin.time.ExperimentalTime

fun evalNumericBinaryExpr(lhs: NumberVal, rhs: NumberVal, op: String, env: Environment): RuntimeVal {
    return when (op) {
        "+" -> {
            // We shall dodge floating point imprecision errors!

            fun getDecimalPlaces(value: Double): Int {
                val stringValue = value.toString()
                return if (stringValue.contains('.')) {
                    stringValue.length - stringValue.indexOf('.') - 1
                } else {
                    0
                }
            }

            val d = (10.0).pow(getDecimalPlaces(max(lhs.value, rhs.value)))
            val l = lhs.value * d
            val r = rhs.value * d
            val lr = l + r

            makeNumber(lr / d)
        }
        "-" -> {
            // We shall dodge floating point imprecision errors!

            fun getDecimalPlaces(value: Double): Int {
                val stringValue = value.toString()
                return if (stringValue.contains('.')) {
                    stringValue.length - stringValue.indexOf('.') - 1
                } else {
                    0
                }
            }

            val d = (10.0).pow(getDecimalPlaces(max(lhs.value, rhs.value)))
            val l = lhs.value * d
            val r = rhs.value * d
            val lr = l - r

            makeNumber(lr / d)
        }
        "*" -> {
            // We shall dodge floating point imprecision errors!

            fun getDecimalPlaces(value: Double): Int {
                val stringValue = value.toString()
                return if (stringValue.contains('.')) {
                    stringValue.length - stringValue.indexOf('.') - 1
                } else {
                    0
                }
            }

            val d = (10.0).pow(getDecimalPlaces(max(lhs.value, rhs.value)))
            val df = (10.0).pow(getDecimalPlaces(lhs.value) + getDecimalPlaces(rhs.value))
            val l = lhs.value * d
            val r = rhs.value * d
            val lr = l * r

            makeNumber(lr / df)
        }
        "/" ->
            if (lhs.value != 0.0 && rhs.value != 0.0) {
                // We shall dodge floating point imprecision errors!

                fun getDecimalPlaces(value: Double): Int {
                    val stringValue = value.toString()
                    return if (stringValue.contains('.')) {
                        stringValue.length - stringValue.indexOf('.') - 1
                    } else {
                        0
                    }
                }

                val d = (10.0).pow(getDecimalPlaces(max(lhs.value, rhs.value)))
                val l = lhs.value * d
                val r = rhs.value * d
                val lr = l / r

                makeNumber(lr / d * 10)
            } else
                throw RuntimeException("Cannot divide by zero.")

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
            val map = HashMap<String, Pair<RuntimeVal, Type>>()

            var inc = (lhs as NumberVal).value
            var idx = 0

            while (inc <= (rhs as NumberVal).value) {
                map["$idx"] = makeNumber(inc) to NumberType()

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

            makeString("$diff")
        }

        else -> throw RuntimeException("Undefined string operator '$op'")
    }
}

@ExperimentalTime
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

@OptIn(ExperimentalTime::class)
fun evalMemberExpr(expr: MemberExpr, env: Environment): RuntimeVal {
    val prop = when (expr.prop) {
        is Identifier -> expr.prop.symbol
        is NumberLiteral -> expr.prop.value.toInt().toString()
        is StringLiteral -> expr.prop.value
        else -> throw Exception("huh")
    }

    val obj = evaluate(expr.obj, env) as ObjectVal

    return obj.value[prop]?.first ?: Error<Nothing> (
        "Attempted to access a property that does not exist in this scope (trying to access ${prop})",
        ""
    ).raise()
}

fun evalIdentifier(identifier: Identifier, env: Environment): RuntimeVal {
    val v = env.getVarNoCache(identifier.symbol)

    return v.value
}

@OptIn(ExperimentalTime::class)
fun evalAssignment(node: AssignmentExpr, env: Environment, file: String): RuntimeVal {
    if (node.assigned is Identifier) {
        val value = evaluate(node.value, env)
        val type = if (node.assigned.type == null) AnyType() else typeEval(node.assigned.type!!, env)

        if (!(type matches value)) {
            Error<Nothing> (
                "IncorrectTypeError: Attempted to assign a value of ${value.kind} (${value.toFancy()}) to ${node.assigned.symbol}, which is a ${type}.",
                file
            )
                .raise()
        }

        return env.assignVar(node.assigned.symbol, value)
    } else if (node.assigned is MemberExpr) {
        val value = evaluate(node.value, env)

        val `object` = evaluate(node.assigned.obj, env) as ObjectVal

        val property = when (node.assigned.prop) {
            is Identifier -> node.assigned.prop.symbol
            is NumberLiteral -> node.assigned.prop.value.toInt().toString()
            is StringLiteral -> node.assigned.prop.value
            else -> throw Exception("huh")
        }

        if (!((`object`.value[property]?.second ?: NeverType()) matches value)) {
            IncorrectTypeError(`object`.value[property]?.second ?: NeverType(), value)
                .raise()
        }

        `object`.value[property] = value to (`object`.value[property]?.second ?: NeverType())

        return value
    }

    throw RuntimeException("Expected an identifier or member expression in an assignment expression.")
}

@OptIn(ExperimentalTime::class)
fun evalObjectExpr(expr: ObjectLiteral, env: Environment): ObjectVal {
    val props = HashMap<String, Pair<RuntimeVal, Type>>()

    expr.properties.forEach {
        val type = if (it.type == null) AnyType() else typeEval(it.type, env)

        if (it.value == null) {
            val value = env.lookupVar(it.key)

            if (!(type matches value)) {
                IncorrectTypeError(type, value)
                    .raise()
            }

            props[it.key] = value to type
        } else {
            props[it.key] = evaluate(it.value, env) to type
        }
    }

    return makeObject(props)
}

@OptIn(ExperimentalTime::class)
fun evalCallExpr(call: CallExpr, env: Environment): RuntimeVal {
    val args = call.args.map { evaluate(it, env) }

    val fn = evaluate(call.caller, env)

    if (fn is NativeFnValue) {
        if (fn.arity > -1 && fn.arity != args.size) {
            when (call.caller) {
                is MemberExpr -> throw RuntimeException("Native Function ${(call.caller.prop as? Identifier)?.symbol} expected ${fn.arity} arguments, instead got ${args.size}.")
                is Identifier -> throw RuntimeException("Native Function ${call.caller.symbol} expected ${fn.arity} arguments, instead got ${args.size}.")
                else -> throw RuntimeException("Native Function UNKNOWN expected ${fn.arity} arguments, instead got ${args.size}.")
            }
        }

        return fn.value(args, env)
    }

    if (fn is FunctionValue) {
        // An arity of -1 means any number of arguments are allowed
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        val scope =
            if (fn.hasModifier(ModifierType.Annotation, "ScopeReadonly")) Environment(env).toReadonly()
            else if (fn.hasModifier(ModifierType.Annotation, "ScopeLimited")) Environment(makeGlobalEnv(arrayOf())).toReadonly()
            else if (fn.hasModifier(ModifierType.Annotation, "ConcurrencyUnprotected")) Environment(env)
            else if (fn.hasModifier(ModifierType.Annotation, "ScopeCopy")) Environment(env.copy())
            else if (!fn.hasModifier(ModifierType.Synchronised)) {
                    Warning("""
                        |Concurrent functions must be annotated with either ScopeReadonly, ScopeLimited or ScopeCopy in v1.0.0-beta.3 (ScopeLimited has been implicitly applied).
                        |This will be an error in future versions.
                        """.trimMargin(), "")
                    .raise()

                Environment(makeGlobalEnv(arrayOf())).toReadonly()
        } else Environment(env)
            /*if (fn.mutating) Environment(fn.declEnv, isCoroutine = fn.coroutine && !fn.promise)
            else Environment(fn.declEnv, isCoroutine = fn.coroutine && !fn.promise).toReadonly()*/

        for ((name, type) in fn.params) {
            if (!(type matches args[name.second.toInt()])) {
                Error<Nothing>(
                    "Parameter ${name.first} at position ${name.second} of function ${fn.name.first ?: "ANONYMOUS"} required a type of ${type}.",
                    ""
                ).raise()
            }

            scope.declareVar(name.first, args[name.second.toInt()], true)
        }

        var result: RuntimeVal = makeNull()

        if (fn.coroutine && !fn.promise) globalCoroutineScope.launch {
            if (!call.hasModifier(ModifierType.Annotation, "CoroutineCall") && fn.coroutine && !call.hasModifier(ModifierType.Annotation, "UncheckedCall")) {
                Warning("As of v1.0.0-beta.3, all calls to coroutines must be marked with @CoroutineCall or @UncheckedCall. This will be an error in future versions.", "")
                    .raise()
            }

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }
        } else if (fn.coroutine) {
            val future = CompletableFuture<RuntimeVal>()

            globalCoroutineScope.launch {
                for (statement in fn.value) {
                    result = evaluate(statement, scope)
                }

                val type = fn.name.second

                require(type matches result) { "Cannot pass argument of type $result.kind} to an expected type of $type." }

                future.complete(result)
            }

            result = makePromise(future)
        } else {
            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }
        }

        val type = fn.name.second

        check(!fn.promise && type matches result) { "Expected ${fn.name.first} to return a ${type}, but actually got ${result.kind}." }

        return result
    }

    throw RuntimeException("Attempted to call a non-function value (${fn.kind}).")
}

@OptIn(ExperimentalTime::class)
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

            for (statement in fn.value) this.launch {
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

@OptIn(ExperimentalTime::class)
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

@OptIn(ExperimentalTime::class)
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
    val params = HashMap<Pair<String, Byte>, Type>()

    for ((_, name, index, type) in decl.block.parameters) {
        params[name to index] = if (type != null) typeEval(type, env) else AnyType()
    }

    val fn = object : FunctionValue(
        name = decl.name?.symbol to if (decl.name?.type != null) typeEval(decl.name.type!!, env) else AnyType(),
        declEnv =
            if (decl.hasModifier(ModifierType.Annotation, "ScopeReadonly")) env.toReadonly()
            else if (decl.hasModifier(ModifierType.Annotation, "ScopeLimited")) makeGlobalEnv(arrayOf()).toReadonly()
            else if (decl.hasModifier(ModifierType.Annotation, "ScopeCopy")) env.copy()
            else if (decl.hasModifier(ModifierType.Annotation, "ConcurrencyUnprotected")) env
            else if (!decl.hasModifier(ModifierType.Synchronised)) {
                Warning("""
                    |Concurrent functions must be annotated with either ScopeReadonly, ScopeLimited or ScopeCopy in v1.0.0-beta.3 (ScopeLimited has been implicitly applied).
                    |This will be an error in future versions.
                """.trimMargin(), "idk")
                    .raise()

                makeGlobalEnv(arrayOf()).toReadonly()
            } else env,
        params = params,
        value = decl.block,
        modifiers = decl.modifiers,
    ) {}

    // The function is not a lambda
    if (fn.name.first != null) {
        env.declareVar(fn.name.first!!, fn, true)
    }

    return fn
}

@OptIn(ExperimentalTime::class)
fun evalLockedBlock(block: LockedBlock, env: Environment): RuntimeVal {
    var result = makeNull() as RuntimeVal

    for (v in block.items) {
        env.getVar(v).lock()
    }

    try {
        for (statement in block.body) {
            result = evaluate(statement, env)
        }
    } finally {
        for (v in block.items) {
            env.getVar(v).unlock()
        }
    }

    return result
}