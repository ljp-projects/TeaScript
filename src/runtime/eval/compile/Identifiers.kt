package runtime.eval.compile

import frontend.AssignmentExpr
import frontend.CallExpr
import frontend.Identifier
import globalVars
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.CompilationEnvironment
import runtime.compile
import runtime.globalVarToDescriptor

fun compileAssignment(node: AssignmentExpr, env: CompilationEnvironment, mv: MethodVisitor, cn: String): MethodVisitor {
    if (node.assigned !is Identifier) {
        throw RuntimeException("Expected an identifier in an assignment expression.")
    }

    compile(node.value, env, mv, cn)
    mv.visitVarInsn(Opcodes.ASTORE, env.getVar(node.assigned.symbol).index)

    return mv
}
fun compileIdentifier(identifier: Identifier, env: CompilationEnvironment, mv: MethodVisitor, cn: String) {
    if (identifier.symbol in globalVars) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, cn, identifier.symbol, globalVarToDescriptor(identifier.symbol))

        return
    }

    mv.visitVarInsn(Opcodes.ALOAD, env.getVar(identifier.symbol).index)
}
fun compileCallExpr(call: CallExpr, env: CompilationEnvironment, mv: MethodVisitor, cn: String): MethodVisitor {
    val args = call.args

    compile(call.caller, env, mv, cn)

    args.forEach {
        compile(it, env, mv, cn)
    }

    mv.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "tea/NativeRunnable",
        "run",
        "(${"Ljava/lang/Object;".repeat(1)})Ljava/lang/Object;",
        false
    )

    return mv
}