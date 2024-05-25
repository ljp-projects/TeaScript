package frontend

enum class TokenType {
    // Literals
    Number, // e.g -1.89
    Str, // e.g. "hello"
    Identifier, // e.g. x
    FuncPrefix, // e.g. x~
    FuncSuffix, // e.g. ~x

    // Keywords
    Mutable,
    Constant,
    Fn,
    After,
    If,
    Otherwise,
    Is,
    Isnt,
    Or,
    Nor,
    And,
    Nand,
    ForEach,
    Await,
    To,
    Import,
    From,
    Class,

    // Modifiers
    Private,
    Sync,
    Promise,
    Lambda,
    Mutating,
    Static,

    // Grouping & operators
    BinaryOperator,
    Equals,
    Comma,
    Dot,
    Colon,
    OpenParen, // (
    CloseParen, // )
    OpenBrace, // {
    CloseBrace, // }
    OpenBracket, // [
    CloseBracket, //]
    EOF,
}

data class Token(val type: TokenType, val value: String = "", val secondary: String = "")