package runtime

import frontend.*
import runtime.eval.transpile.*

fun transpile(astNode: Statement, env: Environment): String {
    return when (astNode) {
        // constant/mutable name = value
        is VarDecl -> transpileVarDecl(astNode, env)
        is Program -> transpileProgram(astNode, env)
        is ForDecl -> transpileForDecl(astNode, env)
        is BinaryExpr -> transpileBinaryExpr(astNode, env)
        is Identifier -> transpileIdentifier(astNode, env)
        is MemberExpr -> transpileMemberExpr(astNode, env)
        is ObjectLiteral -> transpileObjectExpr(astNode, env)
        is CallExpr -> transpileCallExpr(astNode, env)
        is FunctionDecl -> transpileFuncDecl(astNode, env)
        is IfDecl -> transpileIfDecl(astNode, env)
        is AfterDecl -> transpileAfterDecl(astNode, env)
        is NumberLiteral -> astNode.value.toString()
        is StringLiteral -> "\"${astNode.value}\""
        is ImportDecl -> transpileImportDecl(astNode, env)
        is AssignmentExpr -> transpileAssignment(astNode, env)
        else -> throw NotImplementedError("This AST Node hasn't been setup for transpilation yet -- ${astNode.kind}.")
    }
}