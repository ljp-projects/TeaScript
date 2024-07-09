package frontend

import home
import errors.Error
import java.nio.file.Paths
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * The different kinds of modifier.
 * @param appliesTo The different types of expressions and statements the modifier applies to.
 */
enum class ModifierType(private val appliesTo: String) {

    /**
     *The synchronised modifier. Makes actions performed in parallel perform in the main thread.
     * Applies to any block.
     */
    Synchronised("block"),

    /**
     * Makes a function anonymous. Applies to functions only.
     */
    Anonymous("func"),

    /**
     * Makes functions and variables not exposed in a class.
     */
    Private("func,var-decl"),

    /**
     * Makes functions and variables static when compiling to Java bytecode.
     */
    Static("func,var-decl"),

    /**
     * Makes parallel functions return a promise that can be awaited with the return value.
     */
    Promise("func"),

    /**
     * Makes a function able to mutate the class it is a member of.
     */
    Mutating("func"),

    /**
     * Predefined or user-defined options to enable custom behaviour repeatably.
     */
    Annotation("all"),
}

/**
 * The actual modifier itself.
 *
 * @property type The kind of modifier.
 * @property value The value of the modifier.
 * @see ModifierType
 */
data class Modifier(val type: ModifierType, val value: String)

/**
 * The class used to parse TeaScript code.
 * It generates expressions and statements to eb evaluated or compiled later.
 * @see Expr
 * @see Statement
 * @property modifiers The current modifiers that have been parsed. Cleared when used.
 */
class Parser {
    val modifiers: HashSet<Modifier> = hashSetOf()

    private var tokens: MutableList<Token> = ArrayList()
    private var originLen = 0
    private var lines = 0

    private fun notEOF(): Boolean = tokens.firstOrNull()?.type != TokenType.EOF

    private fun at(): Token = tokens.first()

    private fun eat(): Token? = tokens.removeFirstOrNull()

    /**
     * @throws Error Throws when the expected TokenType isn't found.
     */
    private fun expect(type: TokenType, error: String, preserve: Boolean = false): Token {
        val prev = if (preserve) at() else eat()
        if (prev?.type != type) {
            Error<Nothing>(error, "")
                .raise()
        } else {
            return prev
        }
    }

    /**
     * Produces the Abstract Syntax Tree from tokens.
     * @see tokenise
     */
    fun produceAST(sourceCode: String): Program {
        this.lines = sourceCode.lines().size
        this.tokens = tokenise(sourceCode)
        this.originLen = this.tokens.size

        val program: Program = object : Program("program", mutableListOf()) {}

        while (this.notEOF()) {
            program.body.addLast(this.parseStatement())
        }

        return program
    }

    private fun parseStatement(): Statement {
        return when (this.at().type) {
            TokenType.Constant, TokenType.Mutable -> this.parseVarDeclaration()
            TokenType.ForEach -> this.parseForDeclaration()
            TokenType.Import -> this.parseImportDeclaration()
            TokenType.Sync -> {
                this.modifiers.add(
                    Modifier(ModifierType.Synchronised, "YES")
                )
                this.eat()
                this.parseStatement()
            }

            TokenType.Lambda -> {
                this.modifiers.add(
                    Modifier(ModifierType.Anonymous, "YES")
                )
                this.eat()
                this.parseStatement()
            }

            TokenType.Static -> {
                this.modifiers.add(
                    Modifier(ModifierType.Static, "YES")
                )
                this.eat()
                this.parseStatement()
            }

            TokenType.Mutating -> {
                this.modifiers.add(
                    Modifier(ModifierType.Mutating, "YES")
                )
                this.eat()
                this.parseStatement()
            }

            TokenType.Promise -> {
                this.modifiers.add(
                    Modifier(ModifierType.Promise, "YES")
                )
                this.eat()
                this.parseStatement()
            }

            TokenType.Private -> {
                this.modifiers.add(
                    Modifier(ModifierType.Private, "YES")
                )
                this.eat()
                this.parseStatement()
            }

            TokenType.Annotation -> {
                this.modifiers.add(
                    Modifier(ModifierType.Annotation, this.eat()?.value ?: "")
                )
                this.parseStatement()
            }

            else -> this.parseExpr()
        }
    }

    private fun parseImportDeclaration(): Statement {
        eat()

        val imports = parseArgs().map {
            (it as Identifier).symbol
        }.toHashSet()

        expect(
            TokenType.From,
            "Expected from keyword in an import statement."
        )

        if (at().type != TokenType.Str) {
            Error<Nothing>("Argument to use must be a string containing the file name of the module.", "").raise()
        }

        val url = (eat()?.value ?: Error<Nothing>("Argument to use must be included.", "").raise()).replace(
            "@",
            Paths.get(home, ".tea/scripts/").toString() + "/"
        )

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
                Error<Nothing>("Must assign value to constant expression. No value provided.", "").raise()
            }

            return object : VarDecl(
                "var-decl",
                false,
                identifier,
                null
            ) {}
        }

        expect(
            TokenType.Equals,
            "Expected equals token following identifier in var declaration.",
        )

        val declaration = object : VarDecl(
            "var-decl",
            isConstant,
            identifier,
            parseExpr()
        ) {}

        return declaration
    }

    private fun parseArgsUntil(stopAt: TokenType): List<Expr> {
        val args: List<Expr> = if (at().type == TokenType.CloseParen) {
            listOf()
        } else {
            parseArgsList()
        }

        expect(
            stopAt,
            "Expected a $stopAt in an argument list.",
            true
        )

        return args
    }

    private fun parseLockedBlock(): Expr {
        eat() // skip locked token

        return object : LockedBlock(
            parseArgsUntil(TokenType.OpenBrace).map {
                (it as? Identifier)?.symbol ?: Error<Nothing>("Expected identifiers only in locked block.", "").raise()
            }.toHashSet(),
            Block(body = parseBlock())
        ) {}
    }

    private fun parseAwaitDeclaration(): Expr {
        eat()

        val param = parseExpr() as Identifier // Get param

        expect(
            TokenType.From,
            "Expected from after identifier in await expression."
        )

        val promise = parseExpr()

        return object : AwaitDecl(
            "await-decl",
            param,
            promise,
            Block(body = parseBlock()),
            modifiers.toHashSet()
        ) {}.apply { this@Parser.modifiers.clear() }
    }

    private fun parseForDeclaration(): Statement {
        this.eat()

        val arg = parseIdentifier()

        expect(
            TokenType.From,
            "Expected from following identifier in foreach"
        )

        val from = parseExpr()

        println(at())

        return object : ForDecl(
            "for-decl",
            arg,
            from,
            Block(body = parseBlock()),
            modifiers.toHashSet()
        ) {}.apply { this@Parser.modifiers.clear() }
    }

    private fun parseAfterDeclaration(): Expr {
        if (at().type != TokenType.After) {
            return parseOtherOp()
        }

        eat()

        val cond = parseAdditiveExpr()

        return object : AfterDecl(
            "after-decl",
            cond,
            Block(body = parseBlock()),
            modifiers.toHashSet()
        ) {}.apply { this@Parser.modifiers.clear() }
    }

    private fun parseIfDeclaration(): Expr {
        if (at().type != TokenType.If) {
            return parseOtherOp()
        }

        eat()

        val cond = parseOtherOp()

        val body = parseBlock()

        val orBodies: ArrayDeque<OrDecl> = ArrayDeque()

        while (at().type == TokenType.Or) {
            eat()

            val orCond = parseOtherOp()

            orBodies.addLast(object : OrDecl(
                kind = "or-decl",
                body = Block(body = parseBlock()),
                cond = orCond
            ) {})
        }

        val otherwiseBody = if (at().type == TokenType.Otherwise) {
            eat()

            parseBlock()
        } else null

        return object : IfDecl(
            "if-decl",
            cond,
            Block(body = body),
            modifiers.toHashSet(),
            if (otherwiseBody == null) null else Block(body = otherwiseBody),
            orBodies
        ) {}.apply { this@Parser.modifiers.clear() }
    }

    private fun parseFnDeclaration(): Expr {
        eat()

        val fnMods = modifiers.toHashSet()

        modifiers.clear()

        val name = if (modifiers.none { it.type == ModifierType.Anonymous }) {
            parseIdentifier()
        } else {
            eat()
            null
        }

        val args = parseArgs()
        val params = HashSet<Parameter>()

        for (arg in args) {
            if (arg !is Identifier) {
                Error<Nothing>("Inside function declaration expected parameters to be an identifier.", "").raise()
            }

            params.add(Parameter(name = arg.symbol, index = params.size.toByte(), type = arg.type))
        }

        if (at().type == TokenType.BinaryOperator && at().value == "-") {
            eat()

            require (at().type == TokenType.BinaryOperator && at().value == ">") { "Expected a '>' following a '-' when declaring a function." }

            eat()

            name?.type = parseExpr() as TypeExpr
        }

        return object : FunctionDecl(
            "fn-decl",
            name,
            ParameterBlock(body = parseBlock(), parameters = params),
            fnMods,
        ) {}
    }

    private fun parseBlock(): ArrayList<Statement> {
        expect(
            TokenType.OpenBrace,
            "Expected function body following declaration."
        )

        val body: ArrayList<Statement> = ArrayList()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.addLast(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            "Closing brace expected inside function declaration",
        )

        return body
    }

    private fun parseArgs(): ArrayList<Expr> {
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

    private fun parseArgsList(): ArrayList<Expr> {
        val args: ArrayList<Expr> = ArrayList()

        args.addLast(parseExpr())

        while (at().type == TokenType.Comma && eat() != null) {
            args.addLast(parseExpr())
        }

        return args
    }

    private fun parseExpr(): Expr {
        return when (at().type) {
            TokenType.If -> parseIfDeclaration()
            TokenType.After -> parseAfterDeclaration()
            TokenType.Await -> parseAwaitDeclaration()
            TokenType.Func -> parseFnDeclaration()
            TokenType.Locked -> parseLockedBlock()
            TokenType.Annotation -> {
                modifiers.add(
                    Modifier(ModifierType.Annotation, this.eat()?.value ?: "")
                )
                parseExpr()
            }

            TokenType.Sync -> {
                modifiers.add(
                    Modifier(ModifierType.Synchronised, "YES")
                )
                this.eat()
                parseExpr()
            }

            TokenType.Lambda -> {
                modifiers.add(
                    Modifier(ModifierType.Anonymous, "YES")
                )
                this.eat()
                parseExpr()
            }

            TokenType.Static -> {
                modifiers.add(
                    Modifier(ModifierType.Static, "YES")
                )
                this.eat()
                parseExpr()
            }

            TokenType.Mutating -> {
                modifiers.add(
                    Modifier(ModifierType.Mutating, "YES")
                )
                this.eat()
                parseExpr()
            }

            TokenType.Promise -> {
                modifiers.add(
                    Modifier(ModifierType.Promise, "YES")
                )
                this.eat()
                parseExpr()
            }

            TokenType.Private -> {
                modifiers.add(
                    Modifier(ModifierType.Private, "YES")
                )
                this.eat()
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
            val key = when (at().type) {
                TokenType.Identifier -> expect(
                    TokenType.Identifier,
                    "Object literal key expected",
                ).value

                TokenType.Str -> expect(
                    TokenType.Str,
                    "Object literal key expected",
                ).value

                TokenType.Number -> expect(
                    TokenType.Number,
                    "Object literal key expected",
                ).value

                else -> Error<Nothing>("Expected string or identifier", "")
                    .raise()
            }

            val type: TypeExpr? = parseType()

            // Allows shorthand key: pair -> { key, }
            if (at().type == TokenType.Comma) {
                eat() // advance past comma
                properties.addLast(makeProperty(
                    key,
                    null,
                    type
                ))
                continue
            } // Allows shorthand key: pair -> { key }
            else if (at().type == TokenType.CloseBrace) {
                properties.addLast(makeProperty(
                    key,
                    null,
                    type
                ))
                continue
            }

            // { key = val }
            expect(
                TokenType.Equals,
                "Missing equals following identifier in ObjectExpr",
            )

            val value = parseExpr()

            properties.add(makeProperty(
                key,
                value,
                type
            ))

            if (at().type != TokenType.CloseBrace) {
                expect(
                    TokenType.Comma,
                    "Expected comma or closing bracket following property",
                )
            }
        }

        expect(
            TokenType.CloseBrace,
            "Object literal missing closing brace.",
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
        val callExpr: Expr = object : CallExpr(
            "call-expr",
            parseArgs(),
            caller,
            modifiers.toHashSet()
        ) {}.also { modifiers.clear() }

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
                
                // get prop
                property = parsePrimaryExpr()

                if (property !is Identifier && property !is NumberLiteral && property !is StringLiteral) {
                    Error<Nothing>("Cannot use dot operator without right hand side being a identifier, number or string.", "").raise()
                }
            } else {
                // this allows obj[computedValue]
                computed = true
                property = parseExpr()
                expect(
                    TokenType.CloseBracket,
                    "Missing closing bracket in computed value.",
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
            TokenType.Identifier -> parseIdentifier()
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
                    "Unexpected token found inside parenthesised expression. Expected closing parenthesis.",
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

    private fun parseType(): TypeExpr? =
        if (at().type == TokenType.Colon) {
            eat()
            parseObject() as? TypeExpr
        } else null

    private fun parseIdentifier(): Identifier {
        val name = expect(TokenType.Identifier, "Expected an identifier.").value
        val type = if (at().type == TokenType.Colon) {
            eat()
            parseObject()
        } else null

        return object : Identifier(
            "ident",
            name,
            (type ?: object: Identifier("ident", "any", null) {}) as TypeExpr
        ) {}
    }
}