package frontend

/**
 * These are all the kinds of tokens generated during tokenisation.
 * @see tokenise
 * @see Token
 */
enum class TokenType {
    /*** Literals ***/

    /**
     * A token representing a number in TeaScript.
     * A number written as `-2.9` would be tokenised as
     * `Number("-2.9", "")`
     * @since v1.0.0-beta.1
     */
    Number,

    /**
     * A token representing a string in TeaScript.
     * A string written as `"hello"` would be tokenised as
     * `Str("hello", "")`
     * @since v1.0.0-beta.1
     */
    Str,

    /**
     * A token representing an identifier in TeaScript.
     * An identifier written as `x` would be tokenised as
     * `Identifier("x", "infer")`
     * An identifier written as `x: str` would be tokenised as
     * `Identifier("x", "str")`
     * @since v1.0.0-beta.1
     */
    Identifier, // e.g. x

    /**
     * A token representing an annotation in TeaScript.
     * An annotation written as `@Main` would be tokenised as
     * `Annotation("Main", "")`
     * @since v1.0.0-beta.2
     */
    Annotation, // e.g. @x

    /*** Keywords ***/

    /**
     * A token representing the `mutable` keyword.
     * @since v1.0.0-beta.1
     */
    Mutable,

    /**
     * A token representing the `constant` keyword.
     * @since v1.0.0-beta.1
     */
    Constant,

    /**
     * A token representing the `func` keyword.
     * @since v1.0.0-beta.1
     */
    Func,

    /**
     * A token representing the `after` keyword.
     * @since v1.0.0-beta.1
     */
    After,

    /**
     * A token representing the `if` keyword.
     * @since v1.0.0-beta.1
     */
    If,

    /**
     * A token representing the `otherwise` keyword.
     * @since v1.0.0-beta.2
     */
    Otherwise,

    /**
     * A token representing the `is` keyword.
     * @since v1.0.0-beta.2
     */
    Is,

    /**
     * A token representing the `isnt` keyword.
     * @since v1.0.0-beta.2
     */
    Isnt,

    /**
     * A token representing the `or` keyword.
     * @since v1.0.0-beta.2
     */
    Or,

    /**
     * A token representing the `nor` keyword.
     * @since v1.0.0-beta.2
     */
    Nor,

    /**
     * A token representing the `and` keyword.
     * @since v1.0.0-beta.2
     */
    And,

    /**
     * A token representing the `nand` keyword.
     * @since v1.0.0-beta.2
     */
    Nand,

    /**
     * A token representing the `foreach` keyword.
     * @since v1.0.0-beta.1
     */
    ForEach,

    /**
     * A token representing the `await` keyword.
     * @since v1.0.0-beta.1
     */
    Await,

    /**
     * A token representing the `locked` keyword.
     * @since v1.0.0-beta.3-proto.3
     */
    Locked,

    /**
     * A token representing the `to` keyword.
     * @since v1.0.0-beta.2
     */
    To,

    /**
     * A token representing the `import` keyword.
     * @since v1.0.0-beta.2
     */
    Import,

    /**
     * A token representing the `from` keyword.
     * @since v1.0.0-beta.2
     */
    From,

    /*** Modifiers ***/

    /**
     * A token representing the `private` modifier.
     * @since v1.0.0-beta.2
     */
    Private,

    /**
     * A token representing the `synchronised` modifier.
     * @since v1.0.0-beta.1
     */
    Sync,

    /**
     * A token representing the `promise` modifier.
     * @since v1.0.0-beta.2
     */
    Promise,

    /**
     * A token representing the `lambda/anonymous` modifier.
     * @since v1.0.0-beta.2
     */
    Lambda,

    /**
     * A token representing the `mutating` modifier.
     * @since v1.0.0-beta.2
     */
    Mutating,

    /**
     * A token representing the `static` modifier.
     * @since v1.0.0-beta.2
     */
    Static,

    /*** Grouping & operators ***/

    /**
     * A token representing any binary operator.
     * @since v1.0.0-beta.1
     */
    BinaryOperator,

    /**
     * A token representing the `=` operator.
     * @since v1.0.0-beta.1
     */
    Equals,

    /**
     * A token representing the `,` symbol.
     * @since v1.0.0-beta.1
     */
    Comma,

    /**
     * A token representing the `.` operator.
     * @since v1.0.0-beta.1
     */
    Dot,

    /**
     * A token representing the `:` symbol.
     * @since v1.0.0-beta.1
     */
    Colon,

    /**
     * A token representing the `(` symbol.
     * @since v1.0.0-beta.1
     */
    OpenParen, // (

    /**
     * A token representing the `)` symbol.
     * @since v1.0.0-beta.1
     */
    CloseParen, // )

    /**
     * A token representing the `{` symbol.
     * @since v1.0.0-beta.1
     */
    OpenBrace, // {

    /**
     * A token representing the `}` symbol.
     * @since v1.0.0-beta.1
     */
    CloseBrace, // }

    /**
     * A token representing the `[` symbol.
     * @since v1.0.0-beta.1
     */
    OpenBracket, // [

    /**
     * A token representing the `]` symbol.
     * @since v1.0.0-beta.1
     */
    CloseBracket, //]

    /**
     * A token representing the EOF (end of file).
     * @since v1.0.0-beta.1
     */
    EOF,
}

/**
 * A class containing the type of token and any parameters required for it.
 * @since v1.0.0-beta.1
 * @property type The type of token
 * @property value The primary parameter of the token
 * @property secondary The secondary parameter of the token
 */
data class Token(val type: TokenType, val value: String = "", val secondary: String = "")