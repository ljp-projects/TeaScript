package runtime.eval.compile

import frontend.FunctionDecl
import frontend.IfDecl
import frontend.ModifierType
import frontend.TypeExpr
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.CompilationEnvironment
import runtime.compile
import runtime.typeToDescriptor
import runtime.types.*
import java.util.*

fun compileIfDecl(decl: IfDecl, env: CompilationEnvironment, mv: MethodVisitor, cn: String): MethodVisitor {
    val fn = object : CompilationIfValue(
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
    val scope = CompilationEnvironment(fn.declEnv)

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
        val orScope = CompilationEnvironment(fn.declEnv)

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

    val otherwiseScope = CompilationEnvironment(fn.declEnv)

    for (statement in fn.otherwise ?: ArrayDeque()) {
        compile(statement, otherwiseScope, mv, cn)
    }

    mv.visitJumpInsn(Opcodes.GOTO, stmtEnd)

    mv.visitLabel(stmtEnd)

    return mv
}

fun compileFuncDecl(decl: FunctionDecl, env: CompilationEnvironment, cw: ClassWriter, cn: String): MethodVisitor {
    val params = HashMap<Pair<String, Byte>, Type>()

    for ((name, type) in decl.parameters) {
        params[name] = if (type != null) typeEval(type, env) else AnyType()
    }


    val fn = object : CompilationFunctionValue(
        name = decl.name?.symbol to if (decl.name != null) typeEval(decl.name.type as TypeExpr, env) else AnyType(),
        declEnv = env,
        params = params,
        value = decl.body,
        modifiers = decl.modifiers,
        arity = decl.arity.toInt(),
    ) {}

    val fnScope = CompilationEnvironment(fn.declEnv)

    fn.params.forEach {
        fnScope.declareVar(it.key.first, true, fnScope.variables.size)
    }

    var acc = Opcodes.ACC_PUBLIC

    if (fn.static || fn.name.first == "main") acc += Opcodes.ACC_STATIC

    val mw = if (fn.name.first == "main") {
        cw.visitMethod(acc, fn.name.first, "([Ljava/lang/String;)V", null, null)
    } else if (fn.modifiers.any { it.type == ModifierType.Annotation && it.value == "Main" }) {
        cw.visitMethod(acc, "main", "([Ljava/lang/String;)V", null, null)
    } else cw.visitMethod(
        acc,
        fn.name.first,
        "(${fn.params.entries.sortedBy { it.key.second }.joinToString("") { typeToDescriptor(it.value.toString()) }})${typeToDescriptor("${fn.name.second}")}",
        null,
        null
    )

    mw.visitCode()

    fn.value.forEach {
        compile(it, fnScope, mw, cn)
    }

    mw.visitInsn(Opcodes.RETURN)
    mw.visitMaxs(0, 0)
    mw.visitEnd()

    return mw
}