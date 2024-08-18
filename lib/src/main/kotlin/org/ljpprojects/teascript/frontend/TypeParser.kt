package org.ljpprojects.teascript.frontend

import org.ljpprojects.teascript.errors.Error

open class TypeParser(sourceCode: String): Parser(sourceCode) {
    override fun parseExpr(): TypeExpr {
        return when (at().type) {
            TokenType.OpenParen -> parseFnType()
            TokenType.BinaryOperator -> {
                if (at().value == "<") {
                    parseFnType()
                } else Error<Nothing>("Invalid type from ${at()}", "").raise()
            }
            TokenType.QMark -> parseNullableType()
            TokenType.Identifier -> parseIdentType()
            else -> Error<Nothing>("Invalid type from ${at()}", "").raise()
        }
    }

    private fun parseNullableType(): NullableType {
        eat()

        val base = parseExpr()

        return NullableType(base = base)
    }

    private fun parseIdentType(): TypeExpr {
        val name = eat()!!.value

        val typeParams = if (at().value == "<") {
            eat()

            parseArgsUntil(Token(TokenType.BinaryOperator, ">"))
                .mapIndexed { index, type -> type to index.toByte() }.toHashSet()
        } else hashSetOf()

        if (at().value == "::") {
            eat()

            val entryName = eat()!!.value

            return EnumEntryType(
                name = name,
                entryName = entryName,
                typeParams = typeParams
            )
        }

        return IdentType(
            name = name,
            typeParams = typeParams
        )
    }

    open fun parseFnType(): FuncType {
        val typeParams = if (at().value == "<") {
            eat()

            parseArgsUntil(Token(TokenType.BinaryOperator, ">"))
                .mapIndexed { index, type -> (type as IdentType) to index.toByte() }.toHashSet()
        } else hashSetOf()

        val argTypes = parseArgs()

        require(at().type == TokenType.BinaryOperator && eat()!!.value == "-") {
            "Expected a '-' when indicating a return type for a function type, not ()."
        }

        require(at().type == TokenType.BinaryOperator && eat()!!.value == ">") {
            "Expected a '>' following a '-' when indicating a return type for a function type."
        }

        val returnType = parseExpr()

        return FuncType(
            argTypes = argTypes.mapIndexed { index, type -> type to index.toByte() }.toHashSet(),
            returnType = returnType,
            typeParams = typeParams
        )
    }

    override fun parseArgs(): List<TypeExpr> {
        expect(
            TokenType.OpenParen,
            "Expected an open parentheses in an argument list."
        )

        val args = if (at().type == TokenType.CloseParen) {
            ArrayList()
        } else {
            parseArgsList()
        }

        expect(
            TokenType.CloseParen,
            "Expected a closed parenthesis in an argument list."
        )

        return args
    }

    override fun parseArgsUntil(stopAt: Token, excluding: HashSet<Token>): List<TypeExpr> {
        val args: List<TypeExpr> = if (at() == stopAt) {
            listOf()
        } else {
            parseArgsList(hashSetOf(stopAt))
        }

        expect(
            stopAt.type,
            "Expected a $stopAt in an argument list."
        )

        return args
    }

    override fun parseArgsList(excluding: HashSet<Token>): List<TypeExpr> {
        val args: MutableList<TypeExpr> = mutableListOf()

        args.add(parseExpr())

        while (at().type == TokenType.Comma && eat() != null) {
            args.add(parseExpr())
        }

        return args.toList()
    }

    override fun parseType(): TypeExpr {
        return if (at().type == TokenType.Colon) {
            eat()
            parseExpr()
        } else Error<Nothing>("Expected a type.", "").raise()
    }
}

fun main() {
    val parser = TypeParser("<K, V> (<T> (T) -> K) -> Option<V>::Some")

    println(parser.parseExpr())
}