package org.ljpprojects.teascript.frontend

import org.ljpprojects.teascript.errors.Error
import org.ljpprojects.teascript.home
import org.ljpprojects.teascript.utils.Tuple
import org.ljpprojects.teascript.utils.toTuple
import java.nio.file.Paths
import java.util.*

/**
 * The kinds of modifier.
 * @param appliesTo The types of expressions and statements the modifier applies to.
 */
enum class ModifierType(open val appliesTo: String) {

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
open class Parser(private val sourceCode: String) {
    var modifiers: HashSet<Modifier> = hashSetOf()

    private var tokens: MutableList<Token>
        get() = Companion.tokens
        set(new) { Companion.tokens = new }

    private var originLen = 0
    private var lines = 0

    fun notEOF(): Boolean = tokens.firstOrNull()?.type != TokenType.EOF

    open fun at(): Token = tokens.first()

    open fun eat(): Token? = tokens.removeFirstOrNull()

    companion object {
        var tokens: MutableList<Token> = ArrayList()
    }

    init {
        this.lines = sourceCode.lines().size
        this.originLen = this.tokens.size
    }

    fun abstrDeclParser(): AbstrDeclParser {
        return AbstrDeclParser(sourceCode)
    }

    fun typeParser(): TypeParser {
        return TypeParser(sourceCode)
    }

    /**
     * @throws Error Throws when the expected TokenType isn't found.
     */
    open fun expect(type: TokenType, error: String, preserve: Boolean = false): Token {
        val prev = if (preserve) at() else eat()
        if (prev?.type != type) {
            //Error<Nothing>(error, "")
            //    .raise()
            throw kotlin.Error(error + "\n Got ${prev}.")
        } else {
            return prev
        }
    }

    /**
     * Produces the Abstract Syntax Tree from tokens.
     * @see org.ljpprojects.teascript.frontend.tokenise
     */
    open fun produceAST(): Program {
        val program: Program = object : Program("program", mutableListOf()) {}

        while (this.notEOF()) {
            program.body.add(this.parseStatement())
        }

        return program
    }

    open fun parseStatement(): Statement {
        return when (this.at().type) {
            TokenType.Constant, TokenType.Mutable -> this.parseVarDeclaration()
            TokenType.Return -> parseReturnStatement()
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

    open fun parseImportDeclaration(): Statement {
        eat()

        val imports: HashSet<String> = if (at().value == "*") {
            eat()
            hashSetOf()
        } else parseArgs().map {
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

    open fun parseVarDeclaration(): VarDecl {
        val isConstant = eat()?.type == TokenType.Constant

        val mods = modifiers

        modifiers.clear()

        val identifier = parseIdentifier()

        if (at().type != TokenType.Equals) {
            if (isConstant) {
                Error<Nothing>("Must assign value to constant expression. No value provided.", "").raise()
            }

            return object : VarDecl(
                "var-decl",
                false,
                identifier,
                modifiers,
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
            mods,
            parseExpr()
        ) {}

        return declaration
    }

    open fun parseArgsUntil(stopAt: TokenType): List<Expr> {
        val args: List<Expr> = if (at().type == stopAt) {
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

    open fun parseArgsUntil(stopAt: Token, excluding: HashSet<Token> = hashSetOf(stopAt)): List<Expr> {
        val args: List<Expr> = if (at() == stopAt) {
            listOf()
        } else {
            parseArgsList(hashSetOf(stopAt))
        }

        expect(
            stopAt.type,
            "Expected a $stopAt in an argument list.",
            true
        )

        return args
    }

    open fun parseLockedBlock(): Expr {
        eat() // skip locked token

        return object : LockedBlock(
            parseArgsUntil(TokenType.OpenBrace).map {
                (it as? Identifier)?.symbol ?: Error<Nothing>("Expected identifiers only in locked block.", "").raise()
            }.toHashSet(),
            Block(body = parseBlock())
        ) {}
    }

    open fun parseAwaitDeclaration(): Expr {
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

    open fun parseForDeclaration(): Statement {
        this.eat()

        val arg = parseIdentifier()

        expect(
            TokenType.From,
            "Expected from following identifier in foreach"
        )

        val from = parseExpr()

        return object : ForDecl(
            "for-decl",
            arg,
            from,
            Block(body = parseBlock()),
            modifiers.toHashSet()
        ) {}.apply { this@Parser.modifiers.clear() }
    }

    open fun parseAfterDeclaration(): Expr {
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

    open fun parseIfDeclaration(): Expr {
        eat()

        val cond = parseExpr()

        val body = parseBlock()

        val orBodies: ArrayDeque<OrDecl> = ArrayDeque()

        while (at().type == TokenType.Or) {
            eat()

            val orCond = parseExpr()

            orBodies.add(object : OrDecl(
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

    open fun parseFnDeclaration(): FunctionDecl {
        eat()

        val fnMods = modifiers.toHashSet()

        modifiers.clear()

        var typeParams = listOf<Identifier>()

        val name = if (modifiers.none { it.type == ModifierType.Anonymous }) {
            val t = typeParser().parseExpr() as IdentType

            typeParams = t.typeParams.sortedBy { it.second }.map {
                Identifier(symbol = (it.first as IdentType).name, type = null)
            }

            Identifier(symbol = t.name, type = null)
        } else {
            null
        }

        if (at().value == "<") {
            // parse type parameters

            eat()



            typeParams = parseArgsUntil(Token(TokenType.OpenBrace)).map {
                it as Identifier
            }

            eat()
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

            require(at().type == TokenType.BinaryOperator && at().value == ">") { "Expected a '>' following a '-' when declaring a function." }

            eat()

            name?.type = typeParser().parseExpr()
        }

        return object : FunctionDecl(
            "fn-decl",
            name,
            ParameterBlock(
                body = parseBlock(),
                parameters = params,
                typeParams = typeParams.mapIndexed { index, expr ->
                    Parameter(name = expr.symbol, index = index.toByte(), type = expr.type)
                }.toHashSet()
            ),
            fnMods,
        ) {}
    }

    open fun parseTypeDef(): TypeDecl {
        eat() // consume type keyword

        // get name (optional)
        // if this is null no name was given (anonymous type)
        val name = if (at().type == TokenType.OpenBrace) {
            null
        } else parseIdentifier()

        return object : TypeDecl(
            abstrDeclParser().produceDecl(),
            name = name
        ) {}
    }

    open fun parseObjectBlock(): Pair<HashMap<String, Tuple<Expr, TypeExpr?, Boolean>>, HashSet<Identifier>> {
        var conformsTo = hashSetOf<Identifier>()

        expect(
            TokenType.OpenBrace,
            "Expected body object block."
        )

        val body = hashMapOf<String, Tuple<Expr, TypeExpr?, Boolean>>()

        if (at().type == TokenType.Identifier) {
            val types = hashSetOf(parseIdentifier())

            while (at().value != "-") {
                types.add(parseIdentifier())
            }

            eat()

            if (at().value != ">") {
                Error<Nothing>("Expected a '->' after a list of types for an object to conform to.", "").raise()
            }

            eat()

            conformsTo = types
        }

        while (at().type != TokenType.CloseBrace && notEOF()) {
            when (at().type) {
                TokenType.Constant, TokenType.Mutable -> {
                    val decl = parseVarDeclaration()

                    body[decl.identifier.symbol] = ((decl.value ?: Error<Nothing>("Expected a value for all variables in an object block.", "").raise()) to decl.identifier.type to decl.constant).toTuple()
                }
                TokenType.Func -> {
                    val decl = parseFnDeclaration()
                    val name = decl.name?.symbol ?: Error<Nothing>("Anonymous functions are not allowed as top-level declarations in object blocks.", "").raise()

                    body[name] = (decl to IdentType(name = "any", typeParams = hashSetOf()) to true).toTuple()

                }
                TokenType.Type -> {
                    val decl = parseTypeDef()

                    body[decl.name?.symbol ?: Error<Nothing>("Anonymous types are not allowed as top-level declarations in object blocks.", "").raise()] = (decl to decl to true).toTuple()
                }
                else -> Error<Nothing>("Object blocks do not support top-level ${at()} declarations.", "").raise()
            }
        }

        expect(
            TokenType.CloseBrace,
            "Closing brace expected inside object block",
        )

        return body to conformsTo
    }

    open fun parseBlock(): List<Statement> {
        expect(
            TokenType.OpenBrace,
            "Expected an open brace in a block."
        )

        val body: MutableList<Statement> = mutableListOf()

        while (at().type != TokenType.CloseBrace && notEOF()) {
            body.add(parseStatement())
        }

        expect(
            TokenType.CloseBrace,
            "Closing brace expected inside function declaration",
        )

        return body
    }

    open fun parseArgs(): List<Expr> {
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

    open fun parseArgsList(excluding: HashSet<Token> = hashSetOf()): List<Expr> {
        val args: MutableList<Expr> = mutableListOf()

        args.add(parseExpr())

        while (at().type == TokenType.Comma && eat() != null && at() !in excluding) {
            args.add(parseExpr())
        }

        return args.toList()
    }

    open fun parseExpr(): Expr {
        return when (at().type) {
            TokenType.If -> parseIfDeclaration()
            TokenType.After -> parseAfterDeclaration()
            TokenType.Await -> parseAwaitDeclaration()
            TokenType.Func -> parseFnDeclaration()
            TokenType.Locked -> parseLockedBlock()
            TokenType.Type -> parseTypeDef()
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

    open fun parseAssignmentExpr(): Expr {
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

    open fun parseReturnStatement(): ReturnStatement {
        eat() // skip return keyword

        return object : ReturnStatement(
            parseExpr()
        ) {}
    }

    open fun parseOtherOp(): Expr {
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
            val right = parseExpr()
            return object : BinaryExpr(
                "binary-expr",
                left,
                right,
                operator!!
            ) {}
        }

        return left
    }

    open fun parseObject(): Expr {
        if (at().type !== TokenType.OpenBrace) {
            return parseAdditiveExpr()
        }

        val parsed = parseObjectBlock()
        val properties: List<Property> = parsed.first.map { makeProperty(it.key, it.value.first, it.value.second, it.value.third) }

        return object : ObjectLiteral(
            "obj-lit",
            properties,
            parsed.second
        ) {}
    }

    open fun parseExponentiationExpr(): Expr {
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

    open fun parseAdditiveExpr(): Expr {
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

    open fun parseMultiplicativeExpr(): Expr {
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

    open fun parseCallMemberExpr(): Expr {
        val member = parseMemberExpr()

        if (at().type == TokenType.OpenParen) {
            return parseCallExpr(member)
        }

        return member
    }

    open fun parseCallExpr(caller: Expr): Expr {
        val typeParams = (caller as? Identifier)?.typeParams ?: ((caller as? MemberExpr)?.prop as? Identifier)?.typeParams ?: listOf()

        val callExpr: Expr = object : CallExpr(
            "call-expr",
            parseArgs(),
            typeParams,
            caller,
            modifiers.toHashSet()
        ) {}.also { modifiers.clear() }

        return callExpr
    }

    open fun clone(): Parser {
        return Parser(sourceCode).apply {
            tokens = this@Parser.tokens
            modifiers = this@Parser.modifiers
        }
    }

    open fun parseMemberExpr(): Expr {
        var obj: Expr = parsePrimaryExpr()

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

    open fun parsePrimaryExpr(): Expr {
        val tk = this.at()

        // Determine which token we are currently at and return literal value
        return when (tk.type) {
            // User defined values.
            TokenType.Identifier -> parseIdentifier()
            // Constants and Numeric Constants
            TokenType.Number -> object : NumberLiteral(
                "num-lit",
                this.eat()?.value!!.toBigDecimal()
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

    open fun parseType(): TypeExpr? =
        if (at().type == TokenType.Colon) {
            eat()
            parseObject() as? TypeExpr
        } else null

    open fun parseIdentifier(): Identifier {
        val ident = typeParser().parseExpr() as IdentType
        val name = ident.name/*expect(TokenType.Identifier, "Expected an identifier.").value*/

        val type = if (at().type == TokenType.Colon) {
            eat()
            typeParser().parseExpr()
        } else null

        return object : Identifier(
            "ident",
            name,
            type,
            ident.typeParams.sortedBy { it.second }.map { it.first }
            // (type ?: object: Identifier("ident", "any", null) {}) as TypeExpr
        ) {}
    }
}