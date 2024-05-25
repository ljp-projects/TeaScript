package runtime.eval.compile

import frontend.AssignmentExpr
import frontend.CallExpr
import frontend.Identifier
import globalVars
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*

fun compileAssignment(node: AssignmentExpr, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    if (node.assigned !is Identifier) {
        throw RuntimeException("Expected an identifier in an assignment expression.")
    }

    val value = evaluate(node.value, env)

    compile(node.value, env, mv, cn)
    mv.visitVarInsn(typeToStore(value.kind, evaluate(node.assigned, env).kind), env.getVar(node.assigned.symbol).index)

    return mv
}
fun compileIdentifier(identifier: Identifier, env: Environment, mv: MethodVisitor, cn: String) {
    if (identifier.symbol in globalVars) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, cn, identifier.symbol, globalVarToDescriptor(identifier.symbol))

        return
    }

    mv.visitVarInsn(typeToLoad(evaluate(identifier, env)), env.getVar(identifier.symbol).index)
}
fun compileCallExpr(call: CallExpr, env: Environment, mv: MethodVisitor, cn: String): MethodVisitor {
    val args = call.args
    val fn = evaluate(call.caller, env)

    if (fn is NativeFnValue) {
        // An arity of -1 means any number of arguments are allowed
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        compile(call.caller, env, mv, cn)

        args.forEach {
            compile(it, env, mv, cn)
        }

        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "tea/NativeRunnable",
            "run",
            "(${args.joinToString("") { typeToDescriptor(evaluate(it, env)) }})${globalVarMethodToType(fn.jvmName)}",
            false
        )

        return mv
    }

    if (fn is FunctionValue) {
        // An arity of -1 means any number of arguments are allowed
        if (fn.arity > -1 && fn.arity != args.size) {
            throw RuntimeException("${fn.name} expected ${fn.arity} arguments, instead got ${args.size}.")
        }

        compilePrefix(fn, env, mv, cn)

        args.forEach {
            compile(it, env, mv, cn)
        }

        if (fn.static) {
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                cn,
                fn.name.first!!,
                "(${fn.params.joinToString("") { typeToDescriptor(it.second) }})${typeToDescriptor(fn.name.second ?: "null")}",
                false
            )
        }

        compileSuffix(fn, env, mv, cn)

        return mv
    }

    throw RuntimeException("Attempted to call a non-function value ${fn.kind}.")
}