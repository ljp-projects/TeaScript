package runtime.eval.compile

import frontend.Identifier
import frontend.MemberExpr
import frontend.NumberLiteral
import frontend.StringLiteral
import globalVars
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.CompilationEnvironment
import runtime.compile
import runtime.globalVarToClass

fun compileMemberExpr(expr: MemberExpr, env: CompilationEnvironment, mv: MethodVisitor, cn: String): MethodVisitor {
    val prop = when (expr.prop) {
        is Identifier -> expr.prop.symbol
        is NumberLiteral -> expr.prop.value.toInt().toString()
        is StringLiteral -> expr.prop.value
        else -> throw Exception("huh")
    }

    compile(expr.obj, env, mv, cn)

    if (expr.obj is Identifier && expr.obj.symbol in globalVars) {
        if (expr.obj.symbol == "io") {
            mv.visitFieldInsn(Opcodes.GETSTATIC, cn, "io", "Ltea/IO;")
        }

        mv.visitFieldInsn(Opcodes.GETFIELD, globalVarToClass(expr.obj.symbol), prop, "Ltea/NativeRunnable;")

        return mv
    }

    compile(expr.prop, env, mv, cn)
    mv.visitInsn(Opcodes.AALOAD)

    println(expr.obj.kind)

    return mv
}