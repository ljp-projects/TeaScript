package org.ljpprojects.teascript.runtime.abstr_decl

import org.ljpprojects.teascript.frontend.AbstrDeclBlock
import org.ljpprojects.teascript.frontend.AbstrFunctionDecl
import org.ljpprojects.teascript.frontend.AbstrVarDecl
import org.ljpprojects.teascript.runtime.Environment
import org.ljpprojects.teascript.runtime.types.*

open class GeneratedType: AnyObjectType() {
    override val superTypes: HashSet<out Type> = hashSetOf(AnyObjectType())

    open var generics: List<Type> = listOf()
}

fun generateType(decl: AbstrDeclBlock, env: Environment, generics: List<UnresolvedType>): Type {
    return object : GeneratedType() {
        override var generics: List<Type> = generics

        override fun matches(v: RuntimeVal): Boolean {
            if (v !is ObjectVal) return false

            val evaluated: List<Pair<String, Type>> = decl.decls.map {
                 when (it) {
                     is AbstrVarDecl -> {
                         it.identifier.symbol to typeEval(it.identifier.type!!, env)
                     }
                     is AbstrFunctionDecl -> {
                         it.name.symbol to FunctionType(
                             typeEval(it.name.type!!, env),
                             it.params.mapIndexed { index, param ->
                                 typeEval(param.type!!, env) to index.toByte()
                             }.toHashSet()
                         )
                     }

                     else -> throw Error("Invalid declaration for type.")
                 }
            }.sortedBy { it.first }

            val mapped = v.toHashMapStored().value.map { it.key to it.value.second }.sortedBy { it.first }

            return mapped.containsAll(evaluated.toHashSet())
        }
    }
}