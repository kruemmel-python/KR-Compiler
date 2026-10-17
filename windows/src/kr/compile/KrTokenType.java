package kr.compile;

public enum KrTokenType {
    // Structural / Punctuation
    LBRACE, RBRACE, LPAREN, RPAREN, LBRACKET, RBRACKET,
    SEMICOLON, COLON, COMMA, DOT, ARROW, FAT_ARROW,
    
    // Operators
    ASSIGN, PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NE, LT, GT, LE, GE, AND, OR, NOT,
    AMPERSAND, PIPE, CARET, TILDE,
    
    // Literals
    STRING_LITERAL,
    RAW_STRING_LITERAL,
    CHAR_LITERAL,
    NUMBER_LITERAL,
    
    // Language constructs
    PREPROCESSOR,   // #include, #define, etc.
    COMMENT,        // //... or /*...*/
    IDENTIFIER,
    
    // Specific language keywords
    KEYWORD_CPP,    // class, namespace, template, constexpr, etc.
    KEYWORD_RUST,   // fn, pub, impl, trait, match, let, mut, use, etc.
    KEYWORD_GO,     // func, package, import, var, type, chan, go, etc.
    KEYWORD_COMMON, // struct, enum, if, else, for, while, return, const
    
    EOF,
    UNKNOWN
}
