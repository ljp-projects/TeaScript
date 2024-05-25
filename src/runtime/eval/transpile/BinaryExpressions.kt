package runtime.eval.transpile

import com.google.gson.Gson
import frontend.BinaryExpr
import frontend.Identifier
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*

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