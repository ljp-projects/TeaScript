package runtime

import frontend.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.eval.compile.*
import java.util.jar.JarOutputStream

val cw = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)

fun globalVarToDescriptor(gv: String): String {
    return when (gv) {
        "io" -> "Ltea/IO;"
        else -> "V"
    }
}

fun globalVarMethodToType(gv: String): String {
    return when (gv) {
        "println", "print", "eprintln", "eprint" -> "V"
        "readln" -> "Ljava/lang/String;"
        else -> "V"
    }
}

fun globalVarToClass(gv: String): String {
    return when (gv) {
        "io" -> "tea/IO"
        else -> "java/lang/Object"
    }
}

fun typeToLoad(t: String): Int {
    return when(t) {
        "number" -> Opcodes.DLOAD
        "bool" -> Opcodes.ILOAD
        else -> Opcodes.LLOAD
    }
}

fun typeToLoad(t: RuntimeVal): Int {
    return when(t) {
        is NumberVal -> Opcodes.DLOAD
        is BoolVal -> Opcodes.ILOAD
        else -> typeToLoad(t.kind)
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

fun typeToStore(t: String, v: Any): Int {
    return when(t) {
        "number" -> Opcodes.DSTORE
        "bool" -> Opcodes.ISTORE
        else ->  {
            when (v) {
                is String -> Opcodes.ASTORE
                is Double -> Opcodes.DSTORE
                is Boolean -> Opcodes.ISTORE
                else -> Opcodes.ASTORE
            }
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

fun typeToStore(t: RuntimeVal): Int {
    return when(t) {
        is NumberVal -> Opcodes.DSTORE
        is BoolVal -> Opcodes.ISTORE
        else -> {
            typeToStore(t.kind, t.value)
        }
    }
}

fun typeToDescriptor(v: RuntimeVal): String {
    return when(v) {
        is NumberVal -> "D"
        is StringVal -> {
            "Ljava/lang/String;"
        }
        is BoolVal -> {
            "Z"
        }
        else -> typeToDescriptor(v.kind)
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

fun compile(astNode: Statement, env: Environment, cw: ClassWriter, cn: String, jar: JarOutputStream): ClassWriter? {
    return when (astNode) {
        is VarDecl -> {
            compileVarDecl(astNode, env, cw)
            null
        }
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

fun compile(astNode: Statement, env: Environment, mw: MethodVisitor, cn: String): MethodVisitor? {
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
            compileVarDecl(astNode, env, mw, cn)
            null
        }
        is CallExpr -> compileCallExpr(astNode, env, mw, cn)
        is IfDecl -> compileIfDecl(astNode, env, mw, cn)
        is MemberExpr -> compileMemberExpr(astNode, env, mw, cn)
        else -> null
    }
}