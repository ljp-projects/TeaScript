package frontend

import home
import java.nio.file.Paths
import java.util.*

class Parser {
    private var sync = false
    private var anon = false
    private var priv = false
    private var static = false
    private var promise = false
    private var mutating = true

    private var funcPrefix: String? = null
    private var funcSuffix: String? = null

    private var tokens: MutableList<Token> = ArrayList()
    private var originLen = 0
    private var lines = 0

    private fun notEOF(): Boolean {
        return tokens.firstOrNull()?.type != TokenType.EOF
    }

    private fun at(): Token {
        return tokens.first()
    }

    private fun eat(): Token? {
        return tokens.removeFirstOrNull()
    }

    /**
     * @throws Error Throws when the expected TokenType isn't found.
     */
    private fun expect(type: TokenType, error: Exception): Token {
        val prev = tokens.removeFirstOrNull()
        if (prev?.type != type) {
            throw error
        } else {
            return prev
        }
    }

    fun produceAST(sourceCode: String): Program = run {
        lines = sourceCode.lines().size

        tokens = tokenise(sourceCode)

        originLen = tokens.size

        val program: Program = object : Program("program", mutableListOf()) {}

        while(notEOF()) {
            program.body.addLast(parseStatement())
        }

        return@run program
    }

    private fun parseStatement(): Statement = run {
        return when (at().type) {
            TokenType.Constant, TokenType.Mutable -> parseVarDeclaration()
            TokenType.ForEach -> parseForDeclaration()
            TokenType.Import -> parseImportDeclaration()
            else -> parseExpr()
        }
    }

    private fun parseImportDeclaration(): Statement {
        eat()

        val imports = parseArgs().map {
            (it as Identifier).symbol
        }.toHashSet()

        expect(
            TokenType.From,
            Exception("Expected from keyword in an import statement.")
        )

        if (at().type != TokenType.Str) {
            throw IllegalArgumentException("Argument to use must be a string containing the file name of the module.")
        }

        val url = (eat()?.value ?: throw IllegalArgumentException("Argument to use must be included.")).replace("@", Paths.get(home, ".tea/scripts/").toString() + "/")

        return object : ImportDecl(
            "use-decl",
            url,
            imports,
            url.startsWith("http://") || url.startsWith("https://")
        ) {}
    }

    private fun parseVarDeclaration(): Statement {
        val isConstant = eat()?.type == TokenType.Constant

        val identifier = parsePrimaryExpr() as Identifier

        if (at().type != TokenType.Equals) {
            if (isConstant) {
                throw Exception("Must assign value to constant expression. No value provided.")
            }

            return object : VarDecl(
                "var-decl",
                false,
                identifier,
                Optional.empty()
            ) {}
        }

        expect(
            TokenType.Equals,
            Exception("Expected equals token following identifier in var declaration."),
        )

        val declaration = object : VarDecl(
            "var-decl",
            isConstant,
            identifier,
            Optional.of(parseExpr())
        ) {}

        return declaration
    }

    private fun parseAwaitDeclaration(): Expr {
        eat()

        val param = parseExpr() as Identifier // Get param

        expect(
            TokenType.From,
            Exception("Expected from after identifier in await expression.")
        )

        val promise = parseExpr()

        expect(
            TokenType.OpenBrace,
            Exception("Expected function body following declaration.")
        )

        val body: ArrayList<Statement> = ArrayList()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            Exception("Closing brace expected inside function declaration"),
        )

        return object : AwaitDecl(
            "await-decl",
            param,
            promise,
            body,
            !sync
        ) {}.also { sync = false }
    }

    private fun parseForDeclaration(): Statement {
        eat()

        val args = parseArgs()
        val params = ArrayList<Identifier>()

        args.forEach {
            if (it.kind != "ident") {
                throw Exception("Inside function declaration expected parameters to be an identifier.")
            }

            params.addLast((it as Identifier))
        }

        expect(
            TokenType.OpenBrace,
            Exception("Expected function body following declaration.")
        )

        val body: ArrayList<Statement> = ArrayList()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            Exception("Closing brace expected inside function declaration"),
        )

        return object : ForDecl(
            "for-decl",
            params.first(),
            params[1],
            body,
            !sync
        ) {}.also { sync = false }
    }

    private fun parseAfterDeclaration(): Expr {
        if (at().type != TokenType.After) {
            return parseOtherOp()
        }

        eat()

        val cond = parseAdditiveExpr()

        expect(
            TokenType.OpenBrace,
            Exception("Expected function body following declaration.")
        )

        val body = ArrayDeque<Statement>()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            Exception("Closing brace expected inside function declaration"),
        )

        return object : AfterDecl(
            "after-decl",
            cond,
            body,
            !sync
        ) {}.also { sync = false }
    }

    private fun parseIfDeclaration(): Expr {
        if (at().type != TokenType.If) {
            return parseOtherOp()
        }

        eat()

        val cond = parseOtherOp()

        expect(
            TokenType.OpenBrace,
            Exception("Expected function body following declaration.")
        )

        val body = ArrayDeque<Statement>()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            Exception("Closing brace expected inside function declaration"),
        )

        val orBodies: ArrayDeque<OrDecl> = ArrayDeque()

        while (at().type == TokenType.Or) {
            eat()

            val orCond = parseOtherOp()

            expect(
                TokenType.OpenBrace,
                Exception("Expected function body following declaration.")
            )

            val orBodyBacking = ArrayDeque<Statement>()

            while (at().type != TokenType.CloseBrace && notEOF()) {
                orBodyBacking.addLast(parseStatement())
            }

            expect(
                TokenType.CloseBrace,
                Exception("Closing brace expected inside function declaration"),
            )

            orBodies.addLast(object : OrDecl(
                kind = "or-decl",
                body = orBodyBacking,
                cond = orCond
            ) {})
        }

        val otherwiseBody = if (at().type == TokenType.Otherwise) {
            eat()

            expect(
                TokenType.OpenBrace,
                Exception("Expected function body following declaration.")
            )

            val otherWiseBodyBacking = ArrayDeque<Statement>()

            while (at().type != TokenType.CloseBrace && notEOF()) {
                otherWiseBodyBacking.addLast(parseStatement())
            }

            expect(
                TokenType.CloseBrace,
                Exception("Closing brace expected inside function declaration"),
            )

            otherWiseBodyBacking
        } else null

        return object : IfDecl(
            "if-decl",
            cond,
            body,
            !sync,
            otherwiseBody,
            orBodies
        ) {}.also { sync = false }
    }

    private fun parseFnDeclaration(): Expr {
        eat()

        val name = if (!anon) {
            object : Identifier(
                "ident",
                at().value,
                if (at().secondary == "") {
                    eat()
                    "any"
                } else eat()?.secondary!!
            ) {}
        } else null

        val args = parseArgs()
        val params = ArrayDeque<Pair<String, String>>()

        args.forEach {
            if (it !is Identifier) {
                throw Exception("Inside function declaration expected parameters to be an identifier.")
            }

            params.addLast(it.symbol to it.type)
        }

        expect(
            TokenType.OpenBrace,
            Exception("Expected function body following declaration.")
        )

        val body: ArrayList<Statement> = ArrayList()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            Exception("Closing brace expected inside function declaration"),
        )

        return object : FunctionDecl(
            "fn-decl",
            params,
            name,
            body,
            !sync,
            priv,
            params.size,
            promise,
            mutating,
            static,
            funcPrefix,
            funcSuffix
        ) {}.also {
            sync = false
            priv = false
            anon = false
            static = false
            mutating = true
            promise = false
            funcPrefix = null
            funcSuffix = null
        }
    }

    private fun parseArgs(): ArrayList<Expr> {
        expect(
            TokenType.OpenParen,
            Exception("Expected an open parentheses in an argument list.")
        )

        val args = if (at().type == TokenType.CloseParen) {
            ArrayList()
        } else {
            parseArgsList()
        }

        expect(
            TokenType.CloseParen,
            Exception("Expected a closed parenthesis in an argument list.")
        )

        return args
    }

    private fun parseArgsList(): ArrayList<Expr> {
        val args: ArrayList<Expr> = ArrayList()

        args.addLast(parseExpr())

        while (at().type == TokenType.Comma && eat() != null) {
            args.addLast(parseExpr())
        }

        return args
    }

    private fun parseExpr(): Expr {
        return when(at().type) {
            TokenType.If -> parseIfDeclaration()
            TokenType.After -> parseAfterDeclaration()
            TokenType.Await -> parseAwaitDeclaration()
            TokenType.Fn -> parseFnDeclaration()
            TokenType.Class -> parseClassDeclaration()
            TokenType.Sync -> {
                sync = true
                this.eat()
                parseExpr()
            }
            TokenType.Lambda -> {
                anon = true
                this.eat()
                parseExpr()
            }
            TokenType.Static -> {
                static = true
                this.eat()
                parseExpr()
            }
            TokenType.Mutating -> {
                mutating = true
                this.eat()
                parseExpr()
            }
            TokenType.Promise -> {
                promise = true
                this.eat()
                parseExpr()
            }
            TokenType.Private -> {
                priv = true
                this.eat()
                parseExpr()
            }
            TokenType.FuncPrefix -> {
                funcPrefix = eat()?.value
                parseExpr()
            }
            TokenType.FuncSuffix -> {
                funcSuffix = eat()?.value
                parseExpr()
            }
            else -> parseAssignmentExpr()
        }
    }

    private fun parseAssignmentExpr(): Expr {
        val left = parseOtherOp()

        if (at().type == TokenType.Equals) {
            eat() // advance past equals
            val value = parseExpr()
            return object : AssignmentExpr(
                "assign-expr",
                left,
                value
            ) {}
        }

        return left
    }

    private fun parseClassDeclaration(): Expr {
        eat()

        val name = object : Identifier(
            "ident",
            at().value,
            if (at().secondary == "") {
                eat()
                "any"
            } else eat()?.secondary!!,
        ) {}

        val args = parseArgs()
        val params = ArrayDeque<Pair<String, String>>()

        args.forEach {
            if (it !is Identifier) {
                throw Exception("Inside class declaration expected parameters to be an identifier.")
            }

            params.addLast(it.symbol to it.type)
        }

        expect(
            TokenType.OpenBrace,
            Exception("Expected class body following declaration.")
        )

        val body: ArrayList<Statement> = ArrayList()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            Exception("Closing brace expected inside function declaration"),
        )

        return object : ClassDecl(
            "class-decl",
            params,
            name,
            body,
            params.size,
        ) {}
    }

    private fun parseOtherOp(): Expr {
        val left = parseObject()

        if (
            at().value == "is" ||
            at().value == "isnt" ||
            at().value == "or" ||
            at().value == "nor" ||
            at().value == "nand" ||
            at().value == "and" ||
            at().value == "to" ||
            at().value == "<" ||
            at().value == ">" ||
            at().value == "\\>"
        ) {
            val operator = eat()?.value // advance past equals
            val right = parseObject()
            return object : BinaryExpr(
                "binary-expr",
                left,
                right,
                operator!!
            ) {}
        }

        return left
    }

    private fun parseObject(): Expr {
        if (at().type !== TokenType.OpenBrace) {
            return parseAdditiveExpr()
        }

        eat()

        val properties: ArrayList<Property> = arrayListOf()

        while (notEOF() && at().type != TokenType.CloseBrace) {
            val key = expect(
                TokenType.Identifier,
                Exception("Object literal key expected"),
            ).value

            // Allows shorthand key: pair -> { key, }
            if (at().type == TokenType.Comma) {
                eat() // advance past comma
                properties.addLast(object : Property(
                    "prop",
                    key,
                    Optional.empty()
                ) {})
                continue
            } // Allows shorthand key: pair -> { key }
            else if (at().type == TokenType.CloseBrace) {
                properties.addLast(object : Property(
                    "prop",
                    key,
                    Optional.empty()
                ) {})
                continue
            }

            // { key: val }
            expect(
                TokenType.Equals,
                Exception("Missing colon following identifier in ObjectExpr"),
            )

            val value = parseExpr()

            properties.add(object : Property(
                "prop",
                key,
                Optional.of(value)
            ) {})

            if (at().type != TokenType.CloseBrace) {
                expect(
                    TokenType.Comma,
                    Exception("Expected comma or closing bracket following property"),
                )
            }
        }

        expect(
            TokenType.CloseBrace,
            Exception("Object literal missing closing brace."),
        )

        return object : ObjectLiteral(
            "obj-lit",
            properties
        ) {}
    }

    private fun parseExponentiationExpr(): Expr {
        var left = parseCallMemberExpr()

        while (this.at().value == "^") {
            val operator = this.eat()?.value
            val right = parseCallMemberExpr()
            left = object : BinaryExpr(
                "binary-expr",
                left,
                right,
                operator!!
            ) {}
        }

        return left
    }

    private fun parseAdditiveExpr(): Expr {
        var left = parseMultiplicativeExpr()

        while (this.at().value == "+" || this.at().value == "-" || this.at().value == "|") {
            val operator = this.eat()?.value
            val right = parseMultiplicativeExpr()
            left = object : BinaryExpr(
                "binary-expr",
                left,
                right,
                operator!!
            ) {}
        }

        return left
    }

    private fun parseMultiplicativeExpr(): Expr {
        var left = parseExponentiationExpr()

        while (
            this.at().value == "/" ||
            this.at().value == "*" ||
            this.at().value == "%"
        ) {
            val operator = this.eat()?.value
            val right = parseExponentiationExpr()
            left = object : BinaryExpr(
                "binary-expr",
                left,
                right,
                operator!!
            ) {}
        }

        return left
    }

    private fun parseCallMemberExpr(): Expr {
        val member = parseMemberExpr()

        if (at().type == TokenType.OpenParen) {
            return parseCallExpr(member)
        }

        return member
    }

    private fun parseCallExpr(caller: Expr): Expr {
        var callExpr: Expr = object : CallExpr(
            "call-expr",
            parseArgs(),
            caller
        ) {}

        if (at().type == TokenType.OpenParen) {
            callExpr = parseCallExpr(callExpr)
        }

        return callExpr
    }

    private fun parseMemberExpr(): Expr {
        var obj = parsePrimaryExpr()

        while (
            this.at().type == TokenType.Dot ||
            this.at().type == TokenType.OpenBracket
        ) {
            val operator = this.eat()
            var property: Expr
            var computed: Boolean

            // non-computed values aka obj.expr
            if (operator?.type == TokenType.Dot) {
                computed = false
                // get identifier
                property = parsePrimaryExpr()
                if (property !is Identifier) {
                    throw Exception("Cannot use dot operator without right hand side being a identifier")
                }
            } else {
                // this allows obj[computedValue]
                computed = true
                property = parseExpr()
                expect(
                    TokenType.CloseBracket,
                    Exception("Missing closing bracket in computed value."),
                )
            }

            obj = object : MemberExpr(
                "member-expr",
                obj,
                property,
                computed,
            ) {}
        }

        return obj
    }

    private fun parsePrimaryExpr(): Expr {
        val tk = this.at().type

        // Determine which token we are currently at and return literal value
        return when (tk) {
            // User defined values.
            TokenType.Identifier -> object : Identifier(
                "ident",
                at().value,
                if (at().secondary == "") {
                    eat()
                    "infer"
                }
                else eat()?.secondary!!
            ) {}
            // Constants and Numeric Constants
            TokenType.Number -> object : NumberLiteral(
                "num-lit",
                this.eat()?.value!!.toDouble()
            ) {}

            TokenType.Str -> object : StringLiteral(
                "str-lit",
                this.eat()?.value!!
            ) {}
            // Grouping Expressions
            TokenType.OpenParen -> {
                eat() // eat the opening paren
                val value = parseExpr()
                this.expect(
                    TokenType.CloseParen,
                    Exception("Unexpected token found inside parenthesised expression. Expected closing parenthesis."),
                ) // closing paren
                return value
            }

            // Unidentified Tokens and Invalid Code Reached
            else -> {
                error(
                    "Unexpected token found during parsing -- ${at()}"
                )
            }
        }
    }
}