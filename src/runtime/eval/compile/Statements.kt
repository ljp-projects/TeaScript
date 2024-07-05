package runtime.eval.compile

import frontend.Identifier
import frontend.Program
import frontend.VarDecl
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import runtime.CompilationEnvironment
import runtime.compile
import runtime.globalVarToDescriptor
import runtime.typeToStore
import runtime.types.typeEval
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

fun compileProgram(
    program: Program,
    env: CompilationEnvironment,
    cw: ClassWriter,
    cn: String,
    jar: JarOutputStream
): ClassWriter {
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

    return cw
}

fun compileVarDeclaration(declaration: VarDecl, env: CompilationEnvironment, mw: MethodVisitor, cn: String) {
    val t = typeEval(declaration.identifier.type ?: object : Identifier("ident", "any", null) {}, env)

    val g = declaration.value.get()

    compile(g, env, mw, cn)

    env.declareVar(declaration.identifier.symbol, declaration.constant, env.getSize() + 2)

    mw.visitVarInsn(typeToStore(t.toString(), "any"), env.getSize() + 1)
}