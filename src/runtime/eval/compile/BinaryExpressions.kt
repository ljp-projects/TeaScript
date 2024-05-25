package runtime.eval.compile

import com.google.gson.Gson
import frontend.BinaryExpr
import frontend.Identifier
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*

fun compileNumericBinaryExpr(expr: BinaryExpr, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    when (expr.operator) {
        "+" -> {
            compile(expr.left, env, mv, cn)
            compile(expr.right, env, mv, cn)

            mv.visitInsn(Opcodes.DADD)
        }
        "-" -> {
            compile(expr.left, env, mv, cn)
            compile(expr.right, env, mv, cn)

            mv.visitInsn(Opcodes.DSUB)
        }
        "*" -> {
            compile(expr.left, env, mv, cn)
            compile(expr.right, env, mv, cn)

            mv.visitInsn(Opcodes.DMUL)
        }
        "/" -> {
            compile(expr.left, env, mv, cn)
            compile(expr.right, env, mv, cn)

            mv.visitInsn(Opcodes.DDIV)
        }
        "%" -> {
            compile(expr.left, env, mv, cn)
            compile(expr.right, env, mv, cn)

            mv.visitInsn(Opcodes.DREM)
        }
        else -> compileOtherBinaryExpr(expr, env, mv, cn)
    }

    return mv
}
fun compileComparisonBinaryExpr(lhs: RuntimeVal, rhs: RuntimeVal, operator: String, env: Environment, mv: MethodVisitor): MethodVisitor {
    when (operator) {
        "is" -> {
            mv.visitLdcInsn(Gson().toJson(lhs) == Gson().toJson(rhs))
        }
        "isnt" -> mv.visitLdcInsn(Gson().toJson(lhs) != Gson().toJson(rhs))
        "or" -> mv.visitLdcInsn((lhs as BoolVal).value || (rhs as BoolVal).value)
        "nor" -> mv.visitLdcInsn(!((lhs as BoolVal).value || (rhs as BoolVal).value))
        "and" -> mv.visitLdcInsn((lhs as BoolVal).value && (rhs as BoolVal).value)
        "nand" -> mv.visitLdcInsn(!((lhs as BoolVal).value && (rhs as BoolVal).value))
        ">" -> mv.visitLdcInsn(lhs.value as Double > (rhs as NumberVal).value)
        "<" -> mv.visitLdcInsn((lhs.value as Double) < (rhs as NumberVal).value)
        else -> throw RuntimeException("Undefined comparison operator '$operator'")
    }

    return mv
}
fun compileComparisonBinaryExpr(lhs: Identifier, rhs: RuntimeVal, operator: String, env: Environment, mv: MethodVisitor): MethodVisitor {
    when (operator) {
        ">" -> {
            mv.visitVarInsn(Opcodes.DLOAD, env.getVarOrNull(lhs.symbol)?.index ?: -1)
            mv.visitLdcInsn(rhs.value as Double)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "tea/Comparison", "dblGt", "(DD)Z", false)
        }
        "<" -> {
            mv.visitVarInsn(Opcodes.DLOAD, env.getVarOrNull(lhs.symbol)?.index ?: -1)
            mv.visitLdcInsn(rhs.value as Double)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "tea/Comparison", "dblLt", "(DD)Z", false)
        }
        else -> throw RuntimeException("Undefined comparison operator '$operator' for lhs identifier when compiling to the JVM.")
    }

    return mv
}
fun compileOtherBinaryExpr(expr: BinaryExpr, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    when (val op = expr.operator) {
        "to" -> {
            val map = Pair<MutableList<String>, MutableList<RuntimeVal>>(mutableListOf(), mutableListOf())
            val hmap = hashMapOf<String, Any>()

            var inc = (evaluate(expr.left, env) as NumberVal).value
            var idx = 0

            while (inc <= (evaluate(expr.right, env) as NumberVal).value) {
                map.first.addLast(idx.toString())
                map.second.addLast(makeNumber(inc))

                idx++
                inc++
            }

            map.first.forEachIndexed { index, name ->
                hmap[name] = map.second[index]
            }

            mv.visitLdcInsn(
                hmap.toMap()
            )
        }
        else -> return mv
    }

    return mv
}
fun compileIdentBinaryExpr(lhs: Identifier, rhs: Identifier, operator: String, env: Environment, mv: MethodVisitor): MethodVisitor {
    when (operator) {
        "\\>" -> {
            env.getVar(rhs.symbol).peg(env.getVar(lhs.symbol))
        }
        else -> throw RuntimeException("Undefined operator '$operator'")
    }

    return mv
}
fun compileStringBinaryExpr(lhs: StringVal, rhs: RuntimeVal, operator: String, env: Environment, mv: MethodVisitor): MethodVisitor {
    when (operator) {
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

            mv.visitLdcInsn(diff.toString())
        }
        else -> throw RuntimeException("Undefined string operator '$operator'")
    }

    return mv
}
fun compileBinaryExpr(expr: BinaryExpr, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    val (lhs, rhs) = arrayOf(evaluate(expr.left, env), evaluate(expr.right, env))
    val comparisonOps = hashSetOf("is", "isnt", "or", "nor", "and", "nand", ">", "<")

    return if (expr.operator in comparisonOps) {
        if (expr.left is Identifier) {
            return compileComparisonBinaryExpr(expr.left, rhs, expr.operator, env, mv)
        }

        compileComparisonBinaryExpr(lhs, rhs, expr.operator, env, mv)
    } else if (expr.operator == "\\>") {
        compileIdentBinaryExpr(expr.left as Identifier, expr.right as Identifier, expr.operator, env, mv)
    } else if ((lhs is NumberVal && rhs is NumberVal) || (lhs.kind == "number" && rhs.kind == "number") || (lhs is NumberVal && rhs.kind == "number")) {
        compileNumericBinaryExpr(expr, env, mv, cn)
    } else if (lhs is StringVal || lhs.kind == "str") {
        compileStringBinaryExpr(lhs as StringVal, rhs, expr.operator, env, mv)
    } else {
        compileOtherBinaryExpr(expr, env, mv, cn)
    }
}