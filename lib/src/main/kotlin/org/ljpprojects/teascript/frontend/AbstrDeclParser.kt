package org.ljpprojects.teascript.frontend

import org.ljpprojects.teascript.errors.Error

/**
 * This is a good example of an extended parser.
 * It takes advantage of the existing Parser infrastructure
 * and uses it to make a different parser.
 * This parses abstract blocks, which are used in:
 * - Type declarations.
 */
class AbstrDeclParser(sourceCode: String): Parser(sourceCode) {
    override fun parseBlock(): List<AbstractDecl> {
        expect(
            TokenType.OpenBrace,
            "Expected function body following declaration."
        )

        val defs: HashSet<AbstractDecl> = hashSetOf()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            defs.add(parseDecl())
        }

        expect(
            TokenType.CloseBrace,
            "Closing brace expected inside function declaration",
        )

        return defs.toList()
    }

    fun produceDecl(): AbstrDeclBlock {
        return object : AbstrDeclBlock(
            abstrDeclParser().parseBlock(),
            modifiers
        ) {}
    }

    private fun parseDecl(): AbstractDecl {
        return when (at().type) {
            TokenType.Mutable, TokenType.Constant -> parseVarDecl()
            else -> parseFunc()
        }
    }

    private fun parseFunc(): AbstractDecl {
        expect(
            TokenType.Func,
            "Expected a func keyword in an abstract function declaration."
        )

        val fnMods = modifiers.toHashSet()

        modifiers.clear()

        val name = parseIdentifier()

        val args = parseArgs()
        val params = HashSet<Parameter>()

        for (arg in args) {
            if (arg !is Identifier) {
                Error<Nothing>("Inside function declaration expected parameters to be an identifier.", "").raise()
            }

            params.add(Parameter(name = arg.symbol, index = params.size.toByte(), type = arg.type))
        }

        require(at().type == TokenType.BinaryOperator && eat()!!.value == "-") { "Expected a '-' when indicating a return type." }
        require(at().type == TokenType.BinaryOperator && eat()!!.value == ">") { "Expected a '>' following a '-' when declaring a function." }

        name.type = typeParser().parseExpr()

        return object : AbstrFunctionDecl(
            "abstr-fn-decl",
            name,
            params,
            fnMods,
        ) {}
    }

    private fun parseVarDecl(): AbstractDecl {
        val isConstant = eat()?.type == TokenType.Constant

        val identifier = parsePrimaryExpr() as Identifier

        val declaration = object : AbstrVarDecl(
            "abstr-var-decl",
            isConstant,
            identifier,
        ) {}

        return declaration
    }
}