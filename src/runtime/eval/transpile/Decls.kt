package runtime.eval.transpile

import argsParsed
import frontend.AfterDecl
import frontend.FunctionDecl
import frontend.IfDecl
import runtime.*
import runtime.types.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun transpileIfDecl(decl: IfDecl, env: Environment): String {
    val actCond = transpile(decl.cond, env)
    val cond = evaluate(decl.cond, env)

    val fn = object : IfValue(
        cond = cond,
        declEnv = env,
        value = decl.body,
        modifiers = decl.modifiers,
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

    return "$result"
}

fun transpileFuncDecl(decl: FunctionDecl, env: Environment): String {
    val params = HashMap<Pair<String, Byte>, Type>()

    for ((name, type) in decl.parameters) {
        params[name] = if (type != null) typeEval(type, env) else AnyType()
    }

    val fn = object : FunctionValue(
        name = decl.name?.symbol to if (decl.name?.type == null) AnyType() else typeEval(decl.name.type!!, env),
        declEnv = env,
        params = params,
        value = decl.body,
        arity = decl.arity,
        modifiers = decl.modifiers
    ) {}

    if (decl.name?.symbol != null && env.resolve(decl.name.symbol) == null) {
        env.declareVar(decl.name.symbol, fn, true)
    }

    val fnScope = Environment(fn.declEnv)

    fn.params.forEach {
        fnScope.declareVar(it.key.first, makeAny("any"), true)
    }

    val res = StringBuilder("${if (argsParsed.exportAll) "export " else ""}${if (fn.name.first == null) "(" else ""}")

    res.append("function ${fn.name.first ?: ""}(${fn.params.keys.sortedBy { it.second }.joinToString { it.first }}) {\\n")

    fn.value.forEachIndexed { index, statement ->
        res.append("\t")

        if (index == fn.value.size - 1) {
            res.append("return ")
        }

        res.append(transpile(statement, fnScope))
        res.append("\n")
    }

    res.append("}${if (fn.name.first == null) ")" else ""}")

    return "$res"
}

@OptIn(ExperimentalTime::class)
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

    return "$result"
}