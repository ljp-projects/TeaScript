package org.ljpprojects.teascript.frontend

/**
 *  Identifiers can contain some special characters
 *  @ $
 *  It supports kebab and snake case
 *  When subtracting, use a space
 *  They can also be emoji
 */
fun Char.isTeaScriptIdentPart() =
    this.isLetterOrDigit() or (this in setOf('@', '$', '_', '-')) or this.isJavaIdentifierPart()

fun Char.isTeaScriptIdentStart() =
    this.isLetter() or (this in setOf('$', '_')) or this.isJavaIdentifierStart()

/**
 * A list of every reserved word in TeaScript.
 */
val DEFAULT_RESERVED_WORDS: HashMap<String, TokenType> = hashMapOf(
    "mutable" to TokenType.Mutable,
    "constant" to TokenType.Constant,
    "func" to TokenType.Func,
    "after" to TokenType.After,
    "if" to TokenType.If,
    "is" to TokenType.Is,
    "isnt" to TokenType.Isnt,
    "or" to TokenType.Or,
    "foreach" to TokenType.ForEach,
    "to" to TokenType.To,
    "await" to TokenType.Await,
    "nor" to TokenType.Nor,
    "and" to TokenType.And,
    "nand" to TokenType.Nand,
    "synchronised" to TokenType.Sync,
    "otherwise" to TokenType.Otherwise,
    "import" to TokenType.Import,
    "from" to TokenType.From,
    "lambda" to TokenType.Lambda,
    "anonymous" to TokenType.Lambda,
    "private" to TokenType.Private,
    "mutating" to TokenType.Mutating,
    "static" to TokenType.Static,
    "promise" to TokenType.Promise,
    "locked" to TokenType.Locked,
    "type" to TokenType.Type,
    "return" to TokenType.Return
)

/**
 * Given TeaScript source code, generate a list of tokens.
 * @param sourceCode The TeaScript source code to parse.
 * @return A mutable list containing the tokens from the source code.
 * @see Token
 */
fun tokenise(sourceCode: String, reservedWords: HashMap<String, TokenType> = DEFAULT_RESERVED_WORDS): MutableList<Token> {
    val tokens: ArrayDeque<Token> = ArrayDeque(sourceCode.split(" ").size)
    val buf = StringBuilder()
    val chars = sourceCode.toCharArray()
    var charPointer = 0

    while (charPointer < chars.size) {
        when (val c = chars[charPointer]) {
            '"' -> {
                charPointer++

                while (charPointer < chars.size && chars[charPointer] != '"') {
                    if (chars[charPointer] == '\\' && chars[charPointer + 1] == '\"') {
                        buf.append(chars[++charPointer])

                        charPointer++

                        continue
                    }

                    buf.append(chars[charPointer++])
                }

                charPointer++

                tokens.addLast(Token(TokenType.Str, "$buf"))

                buf.clear()
            }

            ':' -> {
                if (chars[charPointer + 1] == ':') {
                    tokens.addLast(Token(TokenType.BinaryOperator, "::"))

                    charPointer += 2

                    continue
                }

                tokens.addLast(Token(TokenType.Colon, chars[charPointer++].toString()))
            }
            '@' -> {
                charPointer++

                while (charPointer < chars.size && !chars[charPointer].isWhitespace()) {
                    buf.append(chars[charPointer++])
                }

                val id = "$buf"

                if (id in reservedWords) {
                    throw Error("Cannot annotate with keywords.")
                }

                tokens.addLast(
                    Token(TokenType.Annotation, id)
                )

                buf.clear()
            }

            '#' -> {
                if (chars[charPointer + 1] == '{') {
                    while (charPointer < chars.size && chars[charPointer] != '}' && chars.getOrNull(charPointer + 1) != '#') {
                        charPointer++
                    }

                    continue
                }

                while (charPointer < chars.size && chars[charPointer] != '\n' && chars[charPointer] != '\r') {
                    charPointer++
                }
            }

            '(' -> tokens.addLast(Token(TokenType.OpenParen, chars[charPointer++].toString()))
            ')' -> tokens.addLast(Token(TokenType.CloseParen, chars[charPointer++].toString()))
            '[' -> tokens.addLast(Token(TokenType.OpenBracket, chars[charPointer++].toString()))
            ']' -> tokens.addLast(Token(TokenType.CloseBracket, chars[charPointer++].toString()))
            '{' -> tokens.addLast(Token(TokenType.OpenBrace, chars[charPointer++].toString()))
            '}' -> tokens.addLast(Token(TokenType.CloseBrace, chars[charPointer++].toString()))
            '=' -> tokens.addLast(Token(TokenType.Equals, chars[charPointer++].toString()))
            ',' -> tokens.addLast(Token(TokenType.Comma, chars[charPointer++].toString()))
            '?' -> tokens.addLast(Token(TokenType.QMark, chars[charPointer++].toString()))
            '!' -> tokens.addLast(Token(TokenType.XMark, chars[charPointer++].toString()))
            '.' -> {
                tokens.addLast(Token(TokenType.Dot, chars[charPointer].toString()))
                charPointer++
            }
            '+', '/', '%', '^', '*', '>', '<', '|' -> tokens.addLast(
                Token(
                    TokenType.BinaryOperator,
                    chars[charPointer++].toString()
                )
            )

            '\\' -> {
                charPointer++

                if (chars.firstOrNull() == '>') {
                    charPointer++
                    tokens.addLast(Token(TokenType.BinaryOperator, "\\>"))
                }
            }

            '-' -> {
                if (chars.getOrNull(1)?.isDigit() == false) {
                    tokens.addLast(Token(TokenType.BinaryOperator, chars[charPointer++].toString()))
                } else {
                    buf.append(chars[charPointer++])

                    while (
                        charPointer < chars.size &&
                        (chars[charPointer].isDigit() ||
                                chars[charPointer] == '.' ||
                                chars[charPointer] == '_')
                    ) {
                        if (chars[charPointer] != '_')
                            buf.append(chars[charPointer++])
                        else
                            chars[charPointer++]
                    }

                    tokens.addLast(Token(TokenType.Number, "$buf"))

                    buf.clear()
                }
            }

            else -> {
                if (c.isDigit()) {
                    buf.append(chars[charPointer++])

                    while (
                        charPointer < chars.size &&
                        (chars[charPointer].isDigit() ||
                                chars[charPointer] == '.' ||
                                chars[charPointer] == '_')
                    ) {
                        if (chars.firstOrNull() != '_')
                            buf.append(chars[charPointer++])
                        else
                            charPointer++
                    }

                    tokens.addLast(Token(TokenType.Number, "$buf"))

                    buf.clear()
                } else if (c.isTeaScriptIdentStart()) {
                    buf.append(chars[charPointer++].toString())

                    while (
                        charPointer < chars.size &&
                        chars[charPointer].isTeaScriptIdentPart()
                    ) {
                        buf.append(chars[charPointer++])
                    }

                    val t: Token = reservedWords["$buf"]?.let {
                        Token(it, "$buf")
                    } ?: Token(TokenType.Identifier, "$buf")

                    tokens.addLast(t)

                    buf.clear()
                } else if (c.isWhitespace()) {
                    charPointer++
                } else {
                    error("Unexpected character '$c' in code.")
                }
            }
        }
    }

    tokens.addLast(Token(TokenType.EOF, "EndOfFile"))

    return tokens
}