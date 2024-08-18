package org.ljpprojects.teascript.frontend

// This file contains expression that are only used in abstract declarations

import java.util.*

interface AbstractDecl: Statement {
    override val kind: String
}

abstract class AbstrDeclBlock(
    val decls: List<AbstractDecl>,
    val modifiers: HashSet<Modifier>
)

/**
 * A statement representing a variable declaration. Kind must be var-decl.
 */
abstract class AbstrVarDecl(
    final override val kind: String = "abstr-var-decl",
    val constant: Boolean,
    val identifier: Identifier,
) : AbstractDecl {
    init {
        require(this.kind == "abstr-var-decl") { "Key can't be ${this.kind}." }
    }
}

/**
 * An expression representing a function declaration. Kind must be fn-decl.
 */
open class AbstrFunctionDecl(
    final override val kind: String = "abstr-fn-decl",
    val name: Identifier,
    val params: HashSet<Parameter>,
    private val modifiers: HashSet<Modifier>,
) : AbstractDecl {
    init {
        require(this.kind == "abstr-fn-decl") { "Key can't be ${this.kind}." }
    }

}