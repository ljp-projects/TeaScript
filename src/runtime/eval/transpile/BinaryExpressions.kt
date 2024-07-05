package runtime.eval.transpile

import frontend.BinaryExpr
import frontend.Identifier
import runtime.Environment
import runtime.transpile
import runtime.types.makeNull

fun transpileComparisonBinaryExpr(lhs: String, rhs: String, op: String): String {
    return when (op) {
        "is" -> "$lhs === $rhs"
        "isnt" -> "$lhs !== $rhs"
        "or" -> "($lhs === true) || ($rhs === true)"
        "nor" -> "!(($lhs === true) || ($rhs === true))"
        "and" -> "($lhs === true) && ($rhs === true)"
        "nand" -> "!(($lhs === true) && ($rhs === true))"
        ">" -> "$lhs > $rhs"
        "<" -> "$lhs < $rhs"
        else -> throw RuntimeException("Undefined comparison operator '$op'")
    }
}

fun transpileOtherBinaryExpr(lhs: String, rhs: String, op: String): String {
    return when (op) {
        "to" -> {
            "__std.rangeInclusive($lhs, $rhs)"
        }

        else -> throw RuntimeException("Undefined operator '$op'")
    }
}

fun transpileBinaryExpr(expr: BinaryExpr, env: Environment): String {
    val (lhs, rhs) = if (expr.left !is Identifier && expr.right !is Identifier && expr.operator == "to") {
        arrayOf(transpile(expr.left, env), transpile(expr.right, env))
    } else {
        arrayOf(makeNull(), makeNull())
    }
    val comparisonOps = hashSetOf("is", "isnt", "or", "nor", "and", "nand", ">", "<")

    return if (expr.operator != "to" && expr.operator !in comparisonOps) {
        "${transpile(expr.left, env)} ${if (expr.operator == "^") "**" else expr.operator} ${
            transpile(
                expr.right,
                env
            )
        }"
    } else if (expr.operator == "to") {
        transpileOtherBinaryExpr(lhs as String, rhs as String, expr.operator)
    } else {
        transpileComparisonBinaryExpr(lhs as String, rhs as String, expr.operator)
    }
}