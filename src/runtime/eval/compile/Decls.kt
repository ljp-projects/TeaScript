package runtime.eval.compile

import frontend.FunctionDecl
import frontend.IfDecl
import frontend.ModifierType
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*
import java.util.*

fun compilePrefix(fn: FunctionValue, env: Environment, mv: MethodVisitor, cn: String) {
    fn.prefixes.forEach {
        val prefix = env.lookupVar(it) as FunctionValue

        compilePrefix(prefix, env, mv, cn)

        for (statement in prefix.value) {
            compile(statement, prefix.declEnv, mv, cn)
        }

        compileSuffix(prefix, env, mv, cn)
    }
}
fun compileSuffix(fn: FunctionValue, env: Environment, mv: MethodVisitor, cn: String) {
    fn.suffixes.forEach {
        val suffix = env.lookupVar(it) as FunctionValue

        compilePrefix(suffix, env, mv, cn)

        for (statement in suffix.value) {
            compile(statement, suffix.declEnv, mv, cn)
        }

        compileSuffix(suffix, env, mv, cn)
    }
}
fun compileIfDecl(decl: IfDecl, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    val fn = object : IfValue(
        cond = makeBool(),
        declEnv = env,
        value = decl.body,
        modifiers = decl.modifiers,
        otherwise = decl.otherwise,
        orStmts = decl.or
    ) {}

    val ifStart = Label()
    val elseStart = Label()
    val orLabels = fn.orStmts.map { Label() }
    val stmtEnd = Label()
    val scope = Environment(fn.declEnv)

    compile(decl.cond, env, mv, cn)
    mv.visitJumpInsn(Opcodes.IFEQ, elseStart)

    compile(decl.cond, env, mv, cn)
    mv.visitJumpInsn(Opcodes.IFNE, ifStart)

    fn.orStmts.forEachIndexed { idx, orDecl ->
        compile(orDecl.cond, env, mv, cn)
        mv.visitJumpInsn(Opcodes.IFNE, orLabels[idx])
        mv.visitJumpInsn(Opcodes.GOTO, stmtEnd)
    }

    mv.visitLabel(ifStart)

    for (statement in fn.value) {
        compile(statement, scope, mv, cn)
    }

    mv.visitJumpInsn(Opcodes.GOTO, stmtEnd)

    fn.orStmts.forEachIndexed { index, orDecl ->
        val orScope = Environment(fn.declEnv)

        mv.visitLabel(orLabels[index])

        for (statement in orDecl.body) {
            compile(statement, orScope, mv, cn)
        }

        mv.visitJumpInsn(Opcodes.GOTO, stmtEnd)
    }

    if (fn.orStmts.size == 0) {
        mv.visitJumpInsn(Opcodes.GOTO, stmtEnd)
    }

    mv.visitLabel(elseStart)

    val otherwiseScope = Environment(fn.declEnv)

    for (statement in fn.otherwise ?: ArrayDeque()) {
        compile(statement, otherwiseScope, mv, cn)
    }

    mv.visitJumpInsn(Opcodes.GOTO, stmtEnd)

    mv.visitLabel(stmtEnd)

    return mv
}
fun compileFuncDecl(decl: FunctionDecl, env: Environment, cw: ClassWriter, cn: String): MethodVisitor {
    val fn = object : FunctionValue(
        name = (decl.name?.symbol ?: "_") to (decl.name?.type ?: "null"),
        declEnv = env,
        params = decl.parameters,
        value = decl.body,
        modifiers = decl.modifiers,
        arity = decl.arity,
    ) {}

    // The function is not a lambda
    if (decl.name != null) {
        env.declareVar(decl.name.symbol, fn, true)
    }

    val fnScope = Environment(fn.declEnv)

    fn.params.forEach {
        fnScope.declareVar(it.first, makeAny(it.second), true, if (fn.static) fnScope.variables.size else 1 + fnScope.variables.size)
    }

    var acc = Opcodes.ACC_PUBLIC

    if (fn.static) acc += Opcodes.ACC_STATIC

    val mw = if (fn.name.first == "main" && fn.static) {
        cw.visitMethod(acc, fn.name.first, "([Ljava/lang/String;)V", null, null)
    } else if (fn.modifiers.any { it.type == ModifierType.Annotation && it.value == "Main" }) {
        cw.visitMethod(acc, "main", "([Ljava/lang/String;)V", null, null)
    } else cw.visitMethod(acc, fn.name.first, "(${fn.params.joinToString("") { typeToDescriptor(it.second) }})${typeToDescriptor(fn.name.second ?: "null")}", null, null)

    mw.visitCode()

    fn.value.forEach {
        compile(it, fnScope, mw, cn)
    }

    mw.visitInsn(Opcodes.RETURN)
    mw.visitMaxs(env.getSize(), env.getSize() + 1)
    mw.visitEnd()

    return mw
}