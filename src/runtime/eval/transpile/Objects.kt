package runtime.eval.transpile

import frontend.*
import runtime.*

fun transpileObjectExpr(expr: ObjectLiteral, env: Environment): String {
    val res = StringBuilder("{")

    expr.properties.forEach {
        val key = if ("-" in it.key) {
            "\"${it.key}\""
        } else it.key

        if (it.value.isEmpty) {
            res.append("$key: ${env.lookupVar(it.key)},")
        } else {
            res.append("$key: ${transpile(it.value.get(), env)},")
        }
    }

    res.append("}")

    return res.toString()
}
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