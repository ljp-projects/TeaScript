package runtime.eval.transpile

import com.google.gson.Gson
import frontend.BinaryExpr
import frontend.Identifier
import runtime.*

fun transpileComparisonBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String): String {
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
fun transpileOtherBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, op: String): String {
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

fun transpileBinaryExpr(expr: BinaryExpr, env: Environment): String {
    val (lhs, rhs) = if (expr.left !is Identifier && expr.right !is Identifier && expr.operator == "to") {
        arrayOf(evaluate(expr.left, env), evaluate(expr.right, env))
    } else {
        arrayOf(makeNull(), makeNull())
    }
    val comparisonOps = hashSetOf("is", "isnt", "or", "nor", "and", "nand", ">", "<")

    return if (expr.operator != "to" && expr.operator !in comparisonOps) {
        "${transpile(expr.left, env)} ${if (expr.operator == "^") "**" else expr.operator} ${transpile(expr.right, env)}"
    } else if (expr.operator == "to") {
        transpileOtherBinaryExpr(lhs, rhs, expr.operator)
    } else {
        transpileComparisonBinaryExpr(lhs, rhs, expr.operator)
    }
}