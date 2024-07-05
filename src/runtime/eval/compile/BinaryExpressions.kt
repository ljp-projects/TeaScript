package runtime.eval.compile

import com.google.gson.Gson
import frontend.BinaryExpr
import frontend.Expr
import frontend.Identifier
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*
import runtime.types.*

fun compileNumericBinaryExpr(
    expr: BinaryExpr,
    env: CompilationEnvironment,
    mv: MethodVisitor,
    cn: String
): MethodVisitor {
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
        else -> compileStringBinaryExpr(expr, expr.operator, env, mv, cn)
    }

    return mv
}
fun compileComparisonBinaryExpr(
    lhs: Expr,
    rhs: Expr,
    operator: String,
    env: CompilationEnvironment,
    mv: MethodVisitor,
    cn: String
): MethodVisitor {
    when (operator) {
        "is" -> {
            mv.visitLdcInsn(Gson().toJson(lhs) == Gson().toJson(rhs))
        }
        "isnt" -> mv.visitLdcInsn(Gson().toJson(lhs) != Gson().toJson(rhs))
        "or" -> mv.visitLdcInsn((lhs as BoolVal).value || (rhs as BoolVal).value)
        "nor" -> mv.visitLdcInsn(!((lhs as BoolVal).value || (rhs as BoolVal).value))
        "and" -> mv.visitLdcInsn((lhs as BoolVal).value && (rhs as BoolVal).value)
        "nand" -> mv.visitLdcInsn(!((lhs as BoolVal).value && (rhs as BoolVal).value))
        ">" -> {
            val zero = Label()
            val one = Label()

            compile(lhs, env, mv, cn)
            compile(rhs, env, mv, cn)
            mv.visitInsn(Opcodes.DCMPG)
            mv.visitJumpInsn(Opcodes.IFGT, one)
            mv.visitJumpInsn(Opcodes.GOTO,  zero)

            mv.visitLabel(zero)
            mv.visitLdcInsn(0.0)

            mv.visitLabel(one)
            mv.visitLdcInsn(1.0)
        }
        "<" -> {
            val zero = Label()
            val one = Label()

            compile(lhs, env, mv, cn)
            compile(rhs, env, mv, cn)
            mv.visitInsn(Opcodes.DCMPL)
            mv.visitJumpInsn(Opcodes.IFLT, one)
            mv.visitJumpInsn(Opcodes.GOTO,  zero)

            mv.visitLabel(zero)
            mv.visitLdcInsn(0.0)

            mv.visitLabel(one)
            mv.visitLdcInsn(1.0)
        }
        else -> throw RuntimeException("Undefined comparison operator '$operator'")
    }

    return mv
}

fun compileIdentBinaryExpr(
    lhs: Identifier,
    rhs: Identifier,
    operator: String,
    env: CompilationEnvironment,
    mv: MethodVisitor
): MethodVisitor {
    when (operator) {
        "\\>" -> {
            env.getVar(rhs.symbol).peg(env.getVar(lhs.symbol))
        }
        else -> throw RuntimeException("Undefined operator '$operator'")
    }

    return mv
}
fun compileStringBinaryExpr(
    expr: BinaryExpr,
    operator: String,
    env: CompilationEnvironment,
    mv: MethodVisitor,
    cn: String
): MethodVisitor {
    when (operator) {
        "+" -> {
            compile(expr.left, env, mv, cn)
            compile(expr.right, env, mv, cn)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
        }

        else -> throw RuntimeException("Undefined operator '$operator'")
    }

    return mv
}
fun compileBinaryExpr(expr: BinaryExpr, env: CompilationEnvironment, mv: MethodVisitor, cn: String): MethodVisitor {
    val comparisonOps = hashSetOf("is", "isnt", "or", "nor", "and", "nand", ">", "<")

    return when (expr.operator) {
        in comparisonOps -> compileComparisonBinaryExpr(expr.left, expr.right, expr.operator, env, mv, cn)
        "\\>" -> compileIdentBinaryExpr(expr.left as Identifier, expr.right as Identifier, expr.operator, env, mv)
        else -> compileNumericBinaryExpr(expr, env, mv, cn)
    }
}