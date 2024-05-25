package frontend

<<<<<<< HEAD
import java.util.ArrayDeque
import java.util.Optional
=======
<<<<<<< HEAD
import java.util.*
=======
import java.util.ArrayDeque
import java.util.Optional
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)

/**
 * Statements don't result in runtime values.
 */
interface Statement {
    val kind: String
}

/**
 * Expressions result in  runtime values, unlike statements.
 */
interface Expr : Statement

abstract class Program(
    final override val kind: String,
    val body: MutableList<Statement>
) : Statement {
    init {
        require(kind == "program") { "Key can't be $kind." }
    }
}

/**
 * A statement representing a variable declaration. Kind must be var-decl.
 */
abstract class VarDecl(
    final override val kind: String,
    val constant: Boolean,
    val identifier: Identifier,
    val value: Optional<Expr>
) : Statement {
    init {
        require(kind == "var-decl") { "Key can't be $kind." }
    }
}

/**
 * An expression representing a function declaration. Kind must be fn-decl.
 */
abstract class FunctionDecl(
    final override val kind: String,
    // Parameters is an array deque of a pair representing name to type
    val parameters: ArrayDeque<Pair<String, String>>,
<<<<<<< HEAD
    val name: Identifier,
=======
<<<<<<< HEAD
    val name: Identifier?,
=======
    val name: Identifier,
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
    val body: List<Statement>,
    val coroutine: Boolean,
    val private: Boolean,
    val arity: Int,
    val promise: Boolean,
<<<<<<< HEAD
=======
<<<<<<< HEAD
    val mutating: Boolean,
    val static: Boolean,
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
    val prefix: String?,
    val suffix: String?
) : Expr {
    init {
        require(kind == "fn-decl") { "Key can't be $kind." }
    }
}

/**
 * An expression representing a function declaration. Kind must be fn-decl.
 */
abstract class ClassDecl(
    final override val kind: String,
    // Parameters is an array deque of a pair representing name to type
    val parameters: ArrayDeque<Pair<String, String>>,
    val name: Identifier,
    val body: List<Statement>,
    val arity: Int
) : Expr {
    init {
        require(kind == "class-decl") { "Key can't be $kind." }
    }
}

/**
 * A statement representing a for loop declaration. Kind must be for-decl.
 */
abstract class ForDecl(
    final override val kind: String,
    val parameter: Identifier,
    val obj: Identifier,
    val body: List<Statement>,
    val async: Boolean,
) : Statement {
    init {
        require(kind == "for-decl") { "Key can't be $kind." }
    }
}

/**
 * A statement representing a use declaration. Kind must be use-decl.
 */
abstract class ImportDecl(
    final override val kind: String,
    val file: String,
    val symbols: HashSet<String>,
    val net: Boolean
) : Statement {
    init {
        require(kind == "use-decl") { "Key can't be $kind." }
    }
}

/**
 * An expression repres enting an await block declaration. Kind must be await-decl.
 */
abstract class AwaitDecl(
    final override val kind: String,
    val parameter: Identifier,
    val obj: Expr,
    val body: List<Statement>,
    val async: Boolean,
) : Expr {
    init {
        require(kind == "await-decl") { "Key can't be $kind." }
    }
}

/**
 * An expression representing an after block declaration. Kind must be after-decl.
 */
abstract class AfterDecl(
    final override val kind: String,
    val ms: Expr,
    val body: ArrayDeque<Statement>,
    val async: Boolean,
) : Expr {
    init {
        require(kind == "after-decl") { "Key can't be $kind." }
    }
}

/**
 * An expression representing an if block declaration. Kind must be if-decl.
 */
abstract class IfDecl(
    final override val kind: String,
    val cond: Expr,
    val body: ArrayDeque<Statement>,
    val async: Boolean,
    val otherwise: ArrayDeque<Statement>?,
    val or: ArrayDeque<OrDecl>,
) : Expr {
    init {
        require(kind == "if-decl") { "Key can't be $kind." }
    }
}

/**
 * A statement representing an or block declaration, which is used after an if. Kind must be or-decl.
 */
abstract class OrDecl(
    final override val kind: String,
    val cond: Expr,
    val body: ArrayDeque<Statement>,
) : Statement {
    init {
        require(kind == "or-decl") { "Key can't be $kind." }
    }
}

/**
 * An expression representing an assignment to a variable. Kind must be assign-expr.
 */
abstract class AssignmentExpr(
    final override val kind: String,
    val assigned: Expr,
    val value: Expr,
) : Expr {
    init {
        require(kind == "assign-expr") { "Key can't be $kind." }
    }
}

/**
 * An expression representing a binary operation of another expression. Kind must be binary-expr.
 */
abstract class BinaryExpr(
    final override val kind: String,
    val left: Expr,
    val right: Expr,
    val operator: String
) : Expr {
    init {
        require(kind == "binary-expr") { "Key can't be $kind." }
    }
}

/**
 * An expression representing a unary operation of another expression. Kind must be unary-expr.
 */
abstract class UnaryExpr(
    final override val kind: String = "unary-expr",
    val expr: Expr,
    val operator: String
) : Expr {
    init {
        require(kind == "unary-expr") { "Key can't be $kind." }
    }
}

/**
 * An expression representing a call to a function. Kind must be call-expr.
 */
abstract class CallExpr(
    final override val kind: String,
    val args: List<Expr>,
    val caller: Expr,
) : Expr {
    init {
        require(kind == "call-expr") { "Key can't be $kind." }
    }
}

/**
 * An expression representing access to an object's member. Kind must be member-expr.
 */
abstract class MemberExpr(
    final override val kind: String,
    val obj: Expr,
    val prop: Expr,
    val computed: Boolean,
) : Expr {
    init {
        require(kind == "member-expr") { "Key can't be $kind." }
    }
}

/**
 * A value representing an identifier. Kind must be ident.
 */
abstract class Identifier(
    final override val kind: String,
    val symbol: String,
<<<<<<< HEAD
    val type: String,
    val init: Boolean
=======
<<<<<<< HEAD
    val type: String
=======
    val type: String,
    val init: Boolean
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
) : Expr {
    init {
        require(kind == "ident") { "Key can't be $kind." }
    }
}

/**
 * A value representing a numeral. Kind must be num-lit.
 */
abstract class NumberLiteral(
    final override val kind: String,
    val value: Double,
) : Expr {
    init {
        require(kind == "num-lit") { "Key can't be $kind." }
    }
}

/**
 * A value representing a string. Kind must be str-lit.
 */
abstract class StringLiteral(
    final override val kind: String,
    val value: String,
) : Expr {
    init {
        require(kind == "str-lit") { "Key can't be $kind." }
    }
}

/**
 * A value representing a property of an object. Kind must be "prop".
 */
abstract class Property(
    final override val kind: String,
    val key: String,
    val value: Optional<Expr>
) : Expr {
    init {
        require(kind == "prop") { "Key can't be $kind." }
    }
}

/**
 * A value representing an object. Kind must be obj-lit.
 */
abstract class ObjectLiteral(
    final override val kind: String,
    val properties: List<Property>,
) : Expr {
    init {
        require(kind == "obj-lit") { "Key can't be $kind." }
    }
}
