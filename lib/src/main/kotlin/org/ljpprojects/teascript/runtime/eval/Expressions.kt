package org.ljpprojects.teascript.runtime.eval

import com.google.gson.Gson
import org.ljpprojects.teascript.errors.Error
import org.ljpprojects.teascript.errors.IncorrectTypeError
import org.ljpprojects.teascript.errors.Warning
import org.ljpprojects.teascript.globalCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ljpprojects.teascript.frontend.*
import org.ljpprojects.teascript.runtime.Environment
import org.ljpprojects.teascript.runtime.abstr_decl.GeneratedType
import org.ljpprojects.teascript.runtime.abstr_decl.generateType
import org.ljpprojects.teascript.runtime.evaluate
import org.ljpprojects.teascript.runtime.makeGlobalEnv
import org.ljpprojects.teascript.runtime.types.*
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime

fun evalTypeDecl(decl: TypeDecl, env: Environment): RuntimeVal {
    val inner = Environment(env)

    val generics = decl.name?.typeParams?.map {
        if (it !is IdentType) {
            throw kotlin.Error("All generic params in a type declaration must be identifier types.")
        }

        val t = typeEval(it, inner) as UnresolvedType

        inner.defineType(it.name, t)

        return@map t
    }!!

    val type = generateType(decl.def, inner, generics)

    env.defineType(decl.name.symbol, type)

    return makeNull()
}

fun evalNumericBinaryExpr(lhs: NumberVal, rhs: NumberVal, op: String, env: Environment): RuntimeVal {
    return when (op) {
        "+" -> makeNumber(lhs.value + rhs.value)
        "-" -> makeNumber(lhs.value - rhs.value)
        "*" -> makeNumber(lhs.value * rhs.value)
        "/" ->
            if (lhs.value != 0.0.toBigDecimal() && rhs.value != 0.0.toBigDecimal()) makeNumber(lhs.value / rhs.value)
            else throw RuntimeException("Cannot divide by zero.")

        "%" -> makeNumber(lhs.value % rhs.value)
        "^" -> makeNumber(lhs.value.pow(rhs.value.toInt()))
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
    val prop = if (!expr.computed) when (expr.prop) {
        is Identifier -> expr.prop.symbol
        is NumberLiteral -> expr.prop.value.toInt().toString()
        is StringLiteral -> expr.prop.value
        else -> throw Exception("huh")
    } else evaluate(expr.prop, env).asString()

    val obj = evaluate(expr.obj, env) as ObjectVal
    val value = obj.value[prop]?.first ?: obj.env?.lookupVar(prop) ?: throw kotlin.Error("Attempted to access a property that does not exist in this object (trying to access ${prop})")

    return value
}

fun evalIdentifier(identifier: Identifier, env: Environment): RuntimeVal {
    val v = env.getVar(identifier.symbol)

    return v.value
}

@OptIn(ExperimentalTime::class)
fun evalAssignment(node: AssignmentExpr, env: Environment, file: String): RuntimeVal {
    if (node.assigned is Identifier) {
        val value = evaluate(node.value, env)
        val type =
            if (value.type == null) AnyType()
            else if (env.getVar(node.assigned.symbol).type != null) env.getVar(node.assigned.symbol).type!!
            else typeEval(node.assigned.type!!, env)

        require(type matches value) {
            "IncorrectTypeError: Attempted to assign a value of type \"${value.type}\" (value of ${value.toFancy()}) to ${node.assigned.symbol}, which is a ${type}."
        }

        return env.assignVar(node.assigned.symbol, value)
    } else if (node.assigned is MemberExpr) {
        val value = evaluate(node.value, env)

        val `object` = evaluate(node.assigned.obj, env) as ObjectVal

        val property = if (!node.assigned.computed) when (node.assigned.prop) {
            is Identifier -> node.assigned.prop.symbol
            is NumberLiteral -> node.assigned.prop.value.toInt().toString()
            is StringLiteral -> node.assigned.prop.value
            else -> throw Exception("huh")
        } else evaluate(node.assigned.prop, env).asString()


        if (!((`object`.value[property]?.second ?: NeverType()) matches value)) {
            IncorrectTypeError(`object`.value[property]?.second ?: NeverType(), value)
                .raise()
        }

        `object`.value[property] = value to (`object`.value[property]?.second ?: NeverType())

        return value
    }

    throw RuntimeException("Expected an identifier or member expression in an assignment expression.")
}

fun evalObjectExpr(expr: ObjectLiteral, env: Environment): ObjectVal {
    val props = HashMap<String, Pair<RuntimeVal, Type>>()
    val objEnv = Environment(env)

    val conformsTo = expr.conformsTo.mapIndexed { idx, t ->
        val type = env.getTypeOrNull(t.symbol)!!

        if (type is GeneratedType) {
            // Resolve types and add them to scope

            // type Foo<T> {constant bar: T}
            // { Foo<number> -> ... }
            val newGenerics = type.generics.mapIndexed { paramIndex, param ->
                if (param !is UnresolvedType) return@mapIndexed param

                val evalType = typeEval(t.typeParams[paramIndex], objEnv)

                objEnv.defineType(param.name, evalType)

                evalType
            }

            type.generics = newGenerics
        }

        type
    }

    expr.properties.forEach {
        val eval = if (it.value == null) makeNull() else evaluate(it.value, objEnv)
        val type = if (it.type == null) getStrictestSubType(eval.type ?: AnyType(), eval).first else typeEval(it.type, objEnv)

        if (it.value == null) {
            val value = objEnv.lookupVar(it.key)

            if (!(type matches value)) {
                IncorrectTypeError(type, value)
                    .raise()
            }

            objEnv.declareVar(it.key, value, it.constant, type, true)
        } else {
            objEnv.declareVar(it.key, evaluate(it.value, objEnv), it.constant, type, true)
        }
    }

    val obj = makeObject(props, env = objEnv)

    if (conformsTo.any { !(it matches obj) }) {
        throw Error("""
            |Object does not conform to its types.
            |${obj.toFancy()}
        """.trimMargin())
    }

    return obj
}

fun evalCall(args: List<RuntimeVal>, typeParams: List<Type> = listOf(), fn: FunctionValue, env: Environment): RuntimeVal {
    if (fn.arity > -1 && fn.arity != args.size) {
        throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
    }

    val scope = fn.declEnv
    /*if (fn.mutating) Environment(fn.declEnv, isCoroutine = fn.coroutine && !fn.promise)
    else Environment(fn.declEnv, isCoroutine = fn.coroutine && !fn.promise).toReadonly()*/

    scope.clear()

    typeParams.forEachIndexed { index, _ ->
        val name = fn.typeParams.keys.sortedBy { it.second }[index].first

        fn.typeParams[name to index.toByte()] = typeParams[index]

        scope.defineType(name, typeParams[index])
    }

    for (
    (name, type) in fn.params.map {
        it.key to when (it.value) {
            is UnresolvedType -> typeEval((it.value as UnresolvedType).expr, env)
            else -> it.value
        }
    }
    ) {
        if (!(type matches args[name.second.toInt()])) {
            throw kotlin.Error(
                """
                    |Parameter ${name.first} at position ${name.second} of function ${fn.name.first ?: "ANONYMOUS"} required a type of ${type}.
                    |Got ${args[name.second.toInt()].toFancy()}
                """.trimMargin()
            )
        }

        val value = args[name.second.toInt()]

        scope.declareVar(name.first, value, true, value.type)
    }

    var result: RuntimeVal = makeNull()

    if (fn.coroutine && !fn.promise) globalCoroutineScope.launch {
        for (statement in fn.value) {
            try {
                result = evaluate(statement, scope)
            } catch (r: Return) {
                result = r.value
                break
            }
        }
    } else if (fn.coroutine) {
        val future = CompletableFuture<RuntimeVal>()

        globalCoroutineScope.launch {
            for (statement in fn.value) {
                try {
                    result = evaluate(statement, scope)
                } catch (r: Return) {
                    result = r.value
                    break
                }
            }

            val type = fn.name.second

            require(type matches result) { "Cannot pass argument of type $result.kind} to an expected type of $type." }

            future.complete(result)
        }

        result = makePromise(future)
    } else {
        for (statement in fn.value) {
            try {
                result = evaluate(statement, scope)
            } catch (r: Return) {
                result = r.value
                break
            }
        }
    }

    val type = fn.name.second

    if (!fn.promise && !(type matches result)) { Error<Nothing>("Expected ${fn.name.first} to return a ${type}, but actually got ${result.kind}.", "").raise() }

    return result
}

@OptIn(ExperimentalTime::class)
fun evalCall(call: CallExpr, fn: FunctionValue, env: Environment): RuntimeVal {
    val args = call.args.map { evaluate(it, env) }
    val typeParams = call.typeParams.map { typeEval(it, env) }

    if (fn.arity > -1 && fn.arity != args.size) {
        throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
    }

    val scope = fn.declEnv
    /*if (fn.mutating) Environment(fn.declEnv, isCoroutine = fn.coroutine && !fn.promise)
    else Environment(fn.declEnv, isCoroutine = fn.coroutine && !fn.promise).toReadonly()*/

    scope.clear()

    typeParams.forEachIndexed { index, _ ->
        val name = fn.typeParams.keys.sortedBy { it.second }[index].first

        fn.typeParams[name to index.toByte()] = typeParams[index]

        scope.defineType(name, typeParams[index])
    }

    for (
    (name, type) in fn.params.map {
        it.key to when (it.value) {
            is UnresolvedType -> typeEval((it.value as UnresolvedType).expr, env)
            else -> it.value
        }
    }
    ) {
        if (!(type matches args[name.second.toInt()])) {
            Error<Nothing>(
                "Parameter ${name.first} at position ${name.second} of function ${fn.name.first ?: "ANONYMOUS"} required a type of ${type}.",
                ""
            ).raise()
        }

        val value = args[name.second.toInt()]

        scope.declareVar(name.first, value, true, value.type)
    }

    var result: RuntimeVal = makeNull()

    if (fn.coroutine && !fn.promise) globalCoroutineScope.launch {
        if (!call.hasModifier(ModifierType.Annotation, "CoroutineCall") && fn.coroutine && !call.hasModifier(
                ModifierType.Annotation, "UncheckedCall")) {
            Warning("As of v1.0.0-beta.3, all calls to coroutines must be marked with @CoroutineCall or @UncheckedCall. This will be an error in future versions.", "")
                .raise()
        }

        for (statement in fn.value) {
            try {
                result = evaluate(statement, scope)
            } catch (r: Return) {
                result = r.value
                break
            }
        }
    } else if (fn.coroutine) {
        val future = CompletableFuture<RuntimeVal>()

        globalCoroutineScope.launch {
            for (statement in fn.value) {
                try {
                    result = evaluate(statement, scope)
                } catch (r: Return) {
                    result = r.value
                    break
                }
            }

            val type = fn.name.second

            require(type matches result) { "Cannot pass argument of type $result.kind} to an expected type of $type." }

            future.complete(result)
        }

        result = makePromise(future)
    } else {
        for (statement in fn.value) {
            try {
                result = evaluate(statement, scope)
            } catch (r: Return) {
                result = r.value
                break
            }
        }
    }

    val type = fn.name.second

    if (!fn.promise && !(type matches result)) { Error<Nothing>("Expected ${fn.name.first} to return a ${type}, but actually got ${result.kind}.", "").raise() }

    return result
}

fun evalCallExpr(call: CallExpr, env: Environment): RuntimeVal {
    val args = call.args.map { evaluate(it, env) }
    val typeParams = call.typeParams.map { typeEval(it, env) }

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
        return evalCall(call, fn, env)
    }

    throw RuntimeException("Attempted to call a non-function value (${fn.kind}).")
}

@OptIn(ExperimentalTime::class)
fun evalAwaitDecl(decl: AwaitDecl, env: Environment): RuntimeVal {
    val fn = object : AwaitValue(
        param = decl.parameter,
        declEnv = env,
        obj = evaluate(decl.obj, env) as AwaitableVal,
        value = decl.body,
        async = decl.modifiers.none { it.type == ModifierType.Synchronised }
    ) {}

    when (fn.obj) {
        is PromiseVal -> {
            val scope = Environment(fn.declEnv)
            var result: RuntimeVal = makeNull()

            val res = fn.obj.value.join()

            scope.declareVar(fn.param.symbol, res, true, res.type)

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }

            return result
        }
    }

    Error<Nothing>("", "").raise()
}

fun evalAfterDecl(decl: AfterDecl, env: Environment): RuntimeVal {
    val ms = evaluate(decl.ms, env)

    val fn = makeAfter(ms, env, decl.body, !decl.modifiers.none { it.type == ModifierType.Synchronised })

    var result: RuntimeVal = makeNull()

    if (fn.async) {
        globalCoroutineScope.launch(Dispatchers.IO) {
            val scope = Environment(fn.declEnv)

            delay((fn.ms as NumberVal).value.toLong())

            for (statement in fn.value) {
                result = evaluate(statement, scope)
            }
        }
    } else runBlocking {
        val scope = Environment(fn.declEnv)

        delay((fn.ms as NumberVal).value.toLong())

        for (statement in fn.value) {
            result = evaluate(statement, scope)
        }
    }

    return result
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

    return result
}

fun evalFuncDecl(decl: FunctionDecl, env: Environment): RuntimeVal {
    val typeParams = hashMapOf(*decl.block.typeParams.map {
        (it.name to it.index) to if (it.type != null) typeEval(it.type, env) else AnyType()
    }.toTypedArray())

    val params = hashMapOf(*decl.block.parameters.map {
        (it.name to it.index) to if (it.type != null) typeEval(it.type, env) else AnyType()
    }.toTypedArray())

    val retType = if (decl.name?.type != null) typeEval(decl.name.type!!, env) else AnyType()

    val fn = FunctionValue(
        name = decl.name?.symbol to retType,
        declEnv =
            if (decl.hasModifier(ModifierType.Annotation, "ScopeReadonly")) Environment(env).toReadonly()
            else if (decl.hasModifier(ModifierType.Annotation, "ScopeLimited")) makeGlobalEnv(arrayOf()).toReadonly()
            else if (decl.hasModifier(ModifierType.Annotation, "ScopeCopy")) Environment(env).copy()
            else if (decl.hasModifier(ModifierType.Annotation, "ConcurrencyUnprotected")) env
            else if (decl.hasModifier(ModifierType.Annotation, "Concurrent")) {
                Warning("""
                    |Concurrent functions must be annotated with either ScopeReadonly, ScopeLimited or ScopeCopy in v1.0.0-beta.3 (ScopeLimited has been implicitly applied).
                    |This will be an error in future versions.
                """.trimMargin(), "idk")
                    .raise()

                makeGlobalEnv(arrayOf()).toReadonly()
            } else Environment(env),
        params = params,
        value = decl.block,
        modifiers = decl.modifiers,
        type = FunctionType(retType, params.map { (name, type) ->
            type to name.second
        }.toHashSet()),
        typeParams = typeParams
    )

    // The function is not a lambda
    if (fn.name.first != null) {
        env.declareVar(fn.name.first!!, fn, true, fn.type, true)
    }

    typeParams.forEach { (name, _) ->
        env.removeType(name.first)
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