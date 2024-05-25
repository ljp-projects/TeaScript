package runtime.eval.compile

import frontend.Program
import frontend.VarDecl
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.*
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.jvm.optionals.getOrNull
import kotlin.time.measureTime

fun compileProgram(program: Program, env: Environment, cw: ClassWriter, cn: String, jar: JarOutputStream): ClassWriter {
    val time = measureTime {
        cw.visit(Opcodes.V1_1, Opcodes.ACC_PUBLIC, cn, null, "java/lang/Object", null)

        cw.visitField(Opcodes.ACC_STATIC + Opcodes.ACC_PUBLIC, "io", globalVarToDescriptor("io"), null, null)

        val clinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
        clinit.visitCode()

        clinit.visitTypeInsn(Opcodes.NEW, "tea/IO")
        clinit.visitInsn(Opcodes.DUP)
        clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, "tea/IO", "<init>", "()V", false)
        clinit.visitFieldInsn(Opcodes.PUTSTATIC, cn, "io", globalVarToDescriptor("io"))

        clinit.visitInsn(Opcodes.RETURN)
        clinit.visitMaxs(2, 0)
        clinit.visitEnd()

        program.body.forEach {
            compile(it, env, cw, cn, jar)
        }

        val entry = JarEntry("$cn.class")

        jar.putNextEntry(entry)
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }

    println()
    println("------------------------------")
    println("Compiled code in $time")
    println("------------------------------")
    println()

    return cw
}
fun compileVarDecl(decl: VarDecl, env: Environment, cw: ClassWriter) {
    val value: RuntimeVal = decl.value.getOrNull().let {
        return@let if (it != null) evaluate(it, env) else null
    } ?: makeNull()

    val type: String = if (env.resolve(decl.identifier.type) != null) {
        env.lookupVar(decl.identifier.type).value.toString()
    } else if (decl.identifier.type == "infer") {
        value.kind
    } else decl.identifier.type

    if (decl.value.isPresent && value.kind != type && type != "any") {
        throw IllegalArgumentException("Expected a value of type $type, instead got ${value.kind}")
    }

    if (decl.constant) {
        cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL + Opcodes.ACC_STATIC, decl.identifier.symbol, typeToDescriptor(value), null, value.value)
    } else {
        cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_STATIC, decl.identifier.symbol, typeToDescriptor(value), null, value.value)
    }

    env.declareVar(decl.identifier.symbol, value, decl.constant)
}
fun compileVarDecl(decl: VarDecl, env: Environment, mw: MethodVisitor, cn: String) {
    val g = decl.value.get()
    val v = evaluate(g, env)

    compile(g, env, mw, cn)

    env.declareVar(decl.identifier.symbol, makeAny(decl.identifier.type), decl.constant, env.getSize() + 2)

    mw.visitVarInsn(typeToStore(v.kind, decl.identifier.type), env.getSize() + 1)
}