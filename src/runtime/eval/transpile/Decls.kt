package runtime.eval.transpile

import argsParsed
import frontend.AfterDecl
import frontend.FunctionDecl
import frontend.IfDecl
import runtime.*

fun transpilePrefix(fn: FunctionValue, env: Environment): String {
    val res = StringBuilder("")

    fn.prefixes.forEach {
        val prefix = env.lookupVar(it) as FunctionValue

        res.append(transpilePrefix(prefix, env))

        res.append("${prefix.name.first}();")

        res.append(transpileSuffix(prefix, env))
    }

    return res.toString()
}
fun transpileSuffix(fn: FunctionValue, env: Environment): String {
    val res = StringBuilder("")

    fn.suffixes.forEach {
        val suffix = env.lookupVar(it) as FunctionValue

        res.append(transpilePrefix(suffix, env))

        res.append("${suffix.name.first}();")

        res.append(transpileSuffix(suffix, env))
    }

    return res.toString()
}
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

    return result.toString()
}
fun transpileFuncDecl(decl: FunctionDecl, env: Environment): String {
    val fn = object : FunctionValue(
        name = decl.name?.symbol to decl.name?.type,
        declEnv = env,
        params = decl.parameters,
        value = decl.body,
        arity = decl.arity,
        modifiers = decl.modifiers
    ) {}

    if (decl.name?.symbol != null && env.resolve(decl.name.symbol) == null) {
        env.declareVar(decl.name.symbol, fn, true)
    }

    val fnScope = Environment(fn.declEnv)

    fn.params.forEach {
        fnScope.declareVar(it.first, makeAny(it.second), true)
    }

    val res = StringBuilder("${if (argsParsed.exportAll) "export " else ""}${if (fn.name.first == null) "(" else ""}function ${fn.name.first ?: ""}(${fn.params.joinToString { it.first }}) {\n")

    fn.value.forEachIndexed { index, statement ->
        res.append("\t")

        if (index == fn.value.size - 1) {
            res.append("return ")
        }

        res.append(transpile(statement, fnScope))
        res.append("\n")
    }

    res.append("}${if (fn.name.first == null) ")" else ""}")

    return res.toString()
}
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