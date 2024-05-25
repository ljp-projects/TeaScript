package frontend

<<<<<<< HEAD
import java.util.LinkedList
=======
<<<<<<< HEAD
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.firstOrNull
import kotlin.collections.getOrNull
import kotlin.collections.hashMapOf
import kotlin.collections.isNotEmpty
import kotlin.collections.removeFirstOrNull
=======
import java.util.LinkedList
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)

val KEYWORDS: HashMap<String, TokenType> = hashMapOf(
    "mutable" to TokenType.Mutable,
    "constant" to TokenType.Constant,
    "func" to TokenType.Fn,
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
    "class" to TokenType.Class,
    "private" to TokenType.Private,
<<<<<<< HEAD
    "noinit" to TokenType.Noinit,
    "promise" to TokenType.Promise
=======
<<<<<<< HEAD
    "promise" to TokenType.Promise,
    "lambda" to TokenType.Lambda,
    // Also allow anonymous keyword to make a lambda
    "anonymous" to TokenType.Lambda,
    "mutating" to TokenType.Mutating,
    "static" to TokenType.Static,
=======
    "noinit" to TokenType.Noinit,
    "promise" to TokenType.Promise
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
)

fun tokenise(sourceCode: String): MutableList<Token> {
    val tokens: ArrayDeque<Token> = ArrayDeque(sourceCode.split(" ").size)
    val buf = StringBuilder()
    val chars = LinkedList(sourceCode.toMutableList())

    while (chars.isNotEmpty()) {
        when (val c = chars.firstOrNull()) {
            '"' -> {
                chars.removeFirst()

                while (chars.firstOrNull() != null && chars.firstOrNull() != '"') {
                    buf.append(chars.removeFirst())
                }

                chars.removeFirstOrNull()

                tokens.addLast(Token(TokenType.Str, buf.toString()))

                buf.clear()
            }
            ':' -> tokens.addLast(Token(TokenType.Colon, chars.removeFirst().toString()))
            '@' -> {
                chars.removeFirstOrNull()

                while (chars.isNotEmpty() && chars.firstOrNull() != '@') {
                    chars.removeFirst()
                }

                chars.removeFirst()
            }
            '#' -> {
                while (chars.isNotEmpty() && chars.firstOrNull() != '\n') {
                    chars.removeFirst()
                }

                chars.removeFirst()
            }
            '(' -> tokens.addLast(Token(TokenType.OpenParen, chars.removeFirst().toString()))
            ')' -> tokens.addLast(Token(TokenType.CloseParen, chars.removeFirst().toString()))
            '[' -> tokens.addLast(Token(TokenType.OpenBracket, chars.removeFirst().toString()))
            ']' -> tokens.addLast(Token(TokenType.CloseBracket, chars.removeFirst().toString()))
            '{' -> tokens.addLast(Token(TokenType.OpenBrace, chars.removeFirst().toString()))
            '}' -> tokens.addLast(Token(TokenType.CloseBrace, chars.removeFirst().toString()))
            '=' -> tokens.addLast(Token(TokenType.Equals, chars.removeFirst().toString()))
            ',' -> tokens.addLast(Token(TokenType.Comma, chars.removeFirst().toString()))
            '.' -> tokens.addLast(Token(TokenType.Dot, chars.removeFirst().toString()))
            '+', '/', '%', '^', '*', '>', '<', '|' -> tokens.addLast(Token(TokenType.BinaryOperator, chars.removeFirst().toString()))
<<<<<<< HEAD
=======
<<<<<<< HEAD
            '\\' -> {
                chars.removeFirst()

                if (chars.firstOrNull() == '>') {
                    chars.removeFirst()
                    tokens.addLast(Token(TokenType.BinaryOperator, "\\>"))
                }
            }
=======
>>>>>>> 05fbf897830ed259e8d85ed4926f11c33f7eebe2
>>>>>>> 0279ede (This is a nightmare)
            '-' -> {
                if (chars.getOrNull(1)?.isDigit() == false) {
                    tokens.addLast(Token(TokenType.BinaryOperator, chars.removeFirst().toString()))
                } else {
                    buf.append(chars.removeFirst())

                    while (chars.firstOrNull()?.isDigit() == true || chars.firstOrNull() == '.' || chars.firstOrNull() == '_') {
                        if (chars.firstOrNull() != '_')
                            buf.append(chars.removeFirst())
                        else
                            chars.removeFirst()
                    }

                    tokens.addLast(Token(TokenType.Number, buf.toString()))

                    buf.clear()
                }
            }
            '~' -> {
                chars.removeFirst()

                buf.append(chars.removeFirst())

                while (chars.firstOrNull()?.isLetter() == true) {
                    buf.append(chars.removeFirst())
                }

                tokens.addLast(Token(TokenType.FuncSuffix, buf.toString()))

                buf.clear()
            }
            else -> {
                if (c?.isDigit() == true) {
                    buf.append(chars.removeFirst())

                    while (chars.firstOrNull()?.isDigit() == true || chars.firstOrNull() == '.' || chars.firstOrNull() == '_') {
                        if (chars.firstOrNull() != '_')
                            buf.append(chars.removeFirst())
                        else
                            chars.removeFirst()
                    }

                    tokens.addLast(Token(TokenType.Number, buf.toString()))

                    buf.clear()
                } else if (c?.isLetter() == true) {
                    buf.append(chars.removeFirst().toString())

                    while (chars.firstOrNull()?.isLetter() == true) {
                        buf.append(chars.removeFirst())
                    }

                    val t: Token = KEYWORDS[buf.toString()]?.let {
                        Token(it, buf.toString())
                    } ?: Token(TokenType.Identifier, buf.toString())

                    while (chars.firstOrNull()?.isWhitespace() == true) chars.removeFirst()

                    if (chars.firstOrNull() == '~' && t.type == TokenType.Identifier) {
                        tokens.addLast(Token(TokenType.FuncPrefix, t.value))
                        chars.removeFirstOrNull()
                    } else if (chars.firstOrNull() == ':' && t.type == TokenType.Identifier) {
                        chars.removeFirst()

                        while (chars.firstOrNull()?.isWhitespace() == true) chars.removeFirst()

                        buf.clear()

                        while (chars.firstOrNull()?.isLetter() == true || chars.firstOrNull() == '-') {
                            buf.append(chars.removeFirst())
                        }

                        tokens.addLast(Token(t.type, t.value, buf.toString()))

                        while (chars.firstOrNull()?.isWhitespace() == true) chars.removeFirst()
                    } else {
                        tokens.addLast(t)
                    }

                    buf.clear()
                } else if (c?.isWhitespace() == true) {
                    chars.removeFirstOrNull()
                } else {
                    error("Unexpected character '$c' in code (character #${sourceCode.length - chars.size}).")
                }
            }
        }
    }

    tokens.addLast(Token(TokenType.EOF, "EndOfFile"))

    return tokens
}