package runtime.eval.compile

import frontend.Identifier
import frontend.MemberExpr
import frontend.NumberLiteral
import frontend.StringLiteral
import globalVars
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*

fun compileMemberExpr(expr: MemberExpr, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    val obj = evaluate(expr.obj, env) as ObjectVal
    val prop = when (expr.prop) {
        is Identifier -> expr.prop.symbol
        is NumberLiteral -> expr.prop.value.toInt().toString()
        is StringLiteral -> expr.prop.value
        else -> throw Exception("huh")
    }

    val idx = obj.value.first.indexOf(prop)

    if (idx == -1) {
        throw IllegalAccessException("Cannot access member $prop as it is either private or doesn't exist.")
    }

    compile(expr.obj, env, mv, cn)

    if (expr.obj is Identifier && expr.obj.symbol in globalVars) {
        if (expr.obj.symbol == "io") {
            mv.visitFieldInsn(Opcodes.GETSTATIC, cn, "io", "Ltea/IO;")
        }

        mv.visitFieldInsn(Opcodes.GETFIELD, globalVarToClass(expr.obj.symbol), prop, "Ltea/NativeRunnable;")
    }

    println(expr.obj.kind)

    return mv
}