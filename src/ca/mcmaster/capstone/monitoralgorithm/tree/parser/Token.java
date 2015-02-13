package ca.mcmaster.capstone.monitoralgorithm.tree.parser;

import java.util.Objects;

import lombok.NonNull;
import lombok.Value;

@Value
public class Token {

    public enum Construct {

        EQUALS(TokenType.BINARY_OP, "=="),
        NOT_EQUALS(TokenType.BINARY_OP, "!="),
        OPEN_PARENS(TokenType.OPEN_PARENS, "("),
        CLOSE_PARENS(TokenType.CLOSE_PARENS, ")"),
        NEGATION(TokenType.UNARY_OP, "!");

        @NonNull private final TokenType tokenType;
        private final String value;

        private Construct(@NonNull final TokenType tokenType, final String value) {
            this.tokenType = tokenType;
            this.value = value;
        }

        public static Construct fromString(@NonNull final String value) {
            for (final Construct construct : values()) {
                if (Objects.equals(construct.value, value)) {
                    return construct;
                }
            }
            return null;
        }

        public Token toToken() {
            return new Token(tokenType, value);
        }
    }

    TokenType tokenType;
    String value;
}
