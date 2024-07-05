package runtime

import frontend.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.eval.compile.*
import java.util.jar.JarOutputStream

val cw = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES)

fun globalVarToDescriptor(gv: String): String {
    return when (gv) {
        "io" -> "Ltea/IO;"
        else -> "V"
    }
}

fun globalVarToClass(gv: String): String {
    return when (gv) {
        "io" -> "tea/IO"
        else -> "java/lang/Object"
    }
}

fun typeToStore(t: String): Int {
    return when(t) {
        "number" -> Opcodes.DSTORE
        "bool" -> Opcodes.ISTORE
        else ->  {
            println(t)
            Opcodes.ASTORE
        }
    }
}

fun typeToStore(t: String, default: String): Int {
    return when(t) {
        "number" -> Opcodes.DSTORE
        "bool" -> Opcodes.ISTORE
        else ->  typeToStore(default)
    }
}

fun typeToDescriptor(t: String): String {
    return when(t) {
        "number" -> {
            "D"
        }
        "str" -> {
            "Ljava/lang/String;"
        }
        "bool" -> {
            "B"
        }
        "null" -> {
            "V"
        }
        "any" -> {
            "Ljava/lang/Object;"
        }
        else -> t
    }
}

fun compile(
    astNode: Statement,
    env: CompilationEnvironment,
    cw: ClassWriter,
    cn: String,
    jar: JarOutputStream
): ClassWriter? {
    return when (astNode) {
        is Program -> {
            compileProgram(astNode, env, cw, cn, jar)
        }
        is FunctionDecl -> {
            compileFuncDecl(astNode, env, cw, cn)
            null
        }
        else -> null
    }
}

fun compile(astNode: Statement, env: CompilationEnvironment, mw: MethodVisitor, cn: String): MethodVisitor? {
    return when (astNode) {
        is BinaryExpr -> compileBinaryExpr(astNode, env, mw, cn)
        is NumberLiteral -> {
            mw.visitLdcInsn(astNode.value)
            null
        }
        is StringLiteral -> {
            mw.visitLdcInsn(astNode.value)
            null
        }
        is Identifier -> {
            compileIdentifier(astNode, env, mw, cn)
            null
        }
        is AssignmentExpr -> compileAssignment(astNode, env, mw, cn)
        is VarDecl -> {
            compileVarDeclaration(astNode, env, mw, cn)
            null
        }
        is CallExpr -> compileCallExpr(astNode, env, mw, cn)
        is IfDecl -> compileIfDecl(astNode, env, mw, cn)
        is MemberExpr -> compileMemberExpr(astNode, env, mw, cn)
        else -> null
    }
}