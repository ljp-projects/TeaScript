package org.ljpprojects.teascript.runtime

import org.ljpprojects.teascript.frontend.*
import org.ljpprojects.teascript.runtime.eval.*
import org.ljpprojects.teascript.runtime.types.RuntimeVal
import org.ljpprojects.teascript.runtime.types.makeNumber
import org.ljpprojects.teascript.runtime.types.makeString

fun evaluate(astNode: Statement, env: Environment, file: String = ""): RuntimeVal {
    return when (astNode) {
        is NumberLiteral -> makeNumber(astNode.value)
        is StringLiteral -> makeString(astNode.value, env)
        is Identifier -> evalIdentifier(astNode, env)
        is ObjectLiteral -> evalObjectExpr(astNode, env)
        is CallExpr -> evalCallExpr(astNode, env)
        is AssignmentExpr -> evalAssignment(astNode, env, file)
        is BinaryExpr -> evalBinaryExpr(astNode, env)
        is Program -> evalProgram(astNode, env)
        is MemberExpr -> evalMemberExpr(astNode, env)
        is VarDecl -> evalVarDecl(astNode, env)
        is FunctionDecl -> evalFuncDecl(astNode, env)
        is AfterDecl -> evalAfterDecl(astNode, env)
        is IfDecl -> evalIfDecl(astNode, env)
        is ForDecl -> evalForDecl(astNode, env)
        is AwaitDecl -> evalAwaitDecl(astNode, env)
        is ImportDecl -> evalImportDecl(astNode, env)
        is LockedBlock -> evalLockedBlock(astNode, env)
        is ReturnStatement -> evalReturnStatement(astNode, env)
        is TypeDecl -> evalTypeDecl(astNode, env)
        else -> throw NotImplementedError("This AST Node hasn't been setup for implementation yet -- ${astNode.kind}.")
    }
}