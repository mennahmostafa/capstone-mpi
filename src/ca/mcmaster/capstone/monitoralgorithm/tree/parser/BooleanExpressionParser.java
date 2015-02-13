package ca.mcmaster.capstone.monitoralgorithm.tree.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import ca.mcmaster.capstone.monitoralgorithm.interfaces.Node;
import ca.mcmaster.capstone.monitoralgorithm.interfaces.Operator;
import ca.mcmaster.capstone.monitoralgorithm.tree.BooleanExpressionTree;
import ca.mcmaster.capstone.monitoralgorithm.tree.ComparisonOperator;
import ca.mcmaster.capstone.monitoralgorithm.tree.LeafNode;
import ca.mcmaster.capstone.monitoralgorithm.tree.RootNode;
import ca.mcmaster.capstone.monitoralgorithm.tree.ValueNode;
import ca.mcmaster.capstone.monitoralgorithm.tree.VariableNode;

public enum BooleanExpressionParser {

    INSTANCE;

    public BooleanExpressionTree parse(final String expression) throws IllegalArgumentException {
        final Stack<Token> tokenStack = new Stack<>();
        for (final Token token : lex(expression)) {
            switch (token.getTokenType()) {
                case OPEN_PARENS:
                    tokenStack.push(token);
                    break;
                case CLOSE_PARENS:
                    final List<Token> tokenList = new ArrayList<>();
                    while (!tokenStack.empty() && !tokenStack.peek().getTokenType().equals(TokenType.OPEN_PARENS)) {
                        tokenList.add(tokenStack.pop());
                    }
                    if (tokenList.size() == 3) { // second child, operator, first child
                        final Token firstChild = tokenList.get(2);
                        final Token operator = tokenList.get(1);
                        final Token secondChild = tokenList.get(0);
                        if (!operator.getTokenType().equals(TokenType.BINARY_OP)) {
                            throw new IllegalArgumentException();
                        }
                        if (!firstChild.getTokenType().equals(TokenType.FLOAT_LITERAL)
                                && !firstChild.getTokenType().equals(TokenType.VARIABLE_IDENTIFIER)) {
                            throw new IllegalArgumentException();
                        }
                        if (!secondChild.getTokenType().equals(TokenType.FLOAT_LITERAL)
                                && !secondChild.getTokenType().equals(TokenType.VARIABLE_IDENTIFIER)) {
                            throw new IllegalArgumentException();
                        }
                        final Node<Double> first, second;
                        if (firstChild.getTokenType().equals(TokenType.FLOAT_LITERAL)) {
                            first = new ValueNode(Double.parseDouble(firstChild.getValue()));
                        } else {
                            first = new VariableNode(firstChild.getValue());
                        }
                        if (secondChild.getTokenType().equals(TokenType.FLOAT_LITERAL)) {
                            second = new ValueNode(Double.parseDouble(secondChild.getValue()));
                        } else {
                            second = new VariableNode(secondChild.getValue());
                        }
                        final RootNode parent = new RootNode(first, second, getBinaryOperatorType(operator.getValue()));
                        return new BooleanExpressionTree(parent);
                        /* FIXME: maybe, if it ends up mattering - this should not just return a new Tree here but rather
                         * add the node to an intermediate tree and return a full tree later.
                         */
                    } else if (tokenList.size() == 2) { // identifier, unary op
                        throw new IllegalArgumentException();
                    } else if (tokenList.size() == 1) {// just identifier
                        throw new IllegalArgumentException();
                    } else { // no idea
                        throw new IllegalArgumentException();
                    }
                case UNARY_OP:
                    tokenStack.push(token);
                    break;
                case BINARY_OP:
                    tokenStack.push(token);
                    break;
                case FLOAT_LITERAL:
                    tokenStack.push(token);
                    break;
                case VARIABLE_IDENTIFIER:
                    tokenStack.push(token);
                    break;
                case EOF:
                    final List<Token> tokenList2 = new ArrayList<>();
                    while (!tokenStack.empty() && !tokenStack.peek().getTokenType().equals(TokenType.OPEN_PARENS)) {
                        tokenList2.add(tokenStack.pop());
                    }
                    if (tokenList2.size() == 3) { // second child, operator, first child
                        final Token firstChild = tokenList2.get(2);
                        final Token operator = tokenList2.get(1);
                        final Token secondChild = tokenList2.get(0);
                        if (!operator.getTokenType().equals(TokenType.BINARY_OP)) {
                            throw new IllegalArgumentException();
                        }
                        if (!firstChild.getTokenType().equals(TokenType.FLOAT_LITERAL)
                                && !firstChild.getTokenType().equals(TokenType.VARIABLE_IDENTIFIER)) {
                            throw new IllegalArgumentException();
                        }
                        if (!secondChild.getTokenType().equals(TokenType.FLOAT_LITERAL)
                                && !secondChild.getTokenType().equals(TokenType.VARIABLE_IDENTIFIER)) {
                            throw new IllegalArgumentException();
                        }
                        final Node<Double> first, second;
                        if (firstChild.getTokenType().equals(TokenType.FLOAT_LITERAL)) {
                            first = new ValueNode(Double.parseDouble(firstChild.getValue()));
                        } else {
                            first = new VariableNode(firstChild.getValue());
                        }
                        if (secondChild.getTokenType().equals(TokenType.FLOAT_LITERAL)) {
                            second = new ValueNode(Double.parseDouble(secondChild.getValue()));
                        } else {
                            second = new VariableNode(secondChild.getValue());
                        }
                        final RootNode parent = new RootNode(first, second, getBinaryOperatorType(operator.getValue()));
                        return new BooleanExpressionTree(parent);
                        /* FIXME: maybe, if it ends up mattering - this should not just return a new Tree here but rather
                         * add the node to an intermediate tree and return a full tree later.
                         */
                    } else if (tokenList2.size() == 2) { // identifier, unary op
                        throw new IllegalArgumentException();
                    } else if (tokenList2.size() == 1) {// just identifier
                        throw new IllegalArgumentException();
                    } else { // no idea
                        throw new IllegalArgumentException();
                    }
                default:
                    throw new IllegalArgumentException();
            }
        }
        throw new IllegalArgumentException();
    }

    private Operator<Double, Boolean> getBinaryOperatorType(final String value) {
        switch (value.trim()) {
            case "==": return ComparisonOperator.EQUAL;
            case "!=": return ComparisonOperator.NOT_EQUAL;
            default: return null;
        }
    }

    private List<Token> lex(final String expression) throws IllegalArgumentException {
        final String exprNoSpaces = expression.replaceAll("\\s+", "");

        final List<Token> tokenList = new ArrayList<>();
        boolean recordingIdentifierOrLiteral = false;
        StringBuilder variableIdentifier = new StringBuilder();
        for (int i = 0; i < exprNoSpaces.length(); ++i) {
            final char c = exprNoSpaces.charAt(i);
            final Token token;
            switch (c) {
                case '!':
                    if (recordingIdentifierOrLiteral) {
                        final String value = variableIdentifier.toString();
                        final TokenType tokenType = isNumericLiteral(value) ? TokenType.FLOAT_LITERAL : TokenType.VARIABLE_IDENTIFIER;
                        tokenList.add(new Token(tokenType, value));
                        variableIdentifier = new StringBuilder();
                    }
                    recordingIdentifierOrLiteral = false;
                    char c2 = exprNoSpaces.charAt(i + 1);
                    if (c2 == '=') {
                        token = Token.Construct.fromString(exprNoSpaces.substring(i, i + 2)).toToken();
                        tokenList.add(token);
                        ++i;
                        continue;
                    } else {
                        token = Token.Construct.fromString(exprNoSpaces.substring(i, i + 1)).toToken();
                        tokenList.add(token);
                        continue;
                    }
                case '=':
                    if (recordingIdentifierOrLiteral) {
                        final String value = variableIdentifier.toString();
                        final TokenType tokenType = isNumericLiteral(value) ? TokenType.FLOAT_LITERAL : TokenType.VARIABLE_IDENTIFIER;
                        tokenList.add(new Token(tokenType, value));
                        variableIdentifier = new StringBuilder();
                    }
                    recordingIdentifierOrLiteral = false;
                    c2 = exprNoSpaces.charAt(i + 1);
                    if (c2 != '=') {
                        throw new IllegalArgumentException();
                    } else {
                        token = Token.Construct.fromString(exprNoSpaces.substring(i, i + 2)).toToken();
                        tokenList.add(token);
                        ++i;
                        continue;
                    }
            }
            final Token.Construct construct = Token.Construct.fromString(exprNoSpaces.substring(i, i + 1));
            if (construct == null) {
                recordingIdentifierOrLiteral = true;
                variableIdentifier.append(exprNoSpaces.charAt(i));
            } else if (construct.equals(Token.Construct.OPEN_PARENS) || construct.equals(Token.Construct.CLOSE_PARENS)) {
                if (recordingIdentifierOrLiteral) {
                    final String value = variableIdentifier.toString();
                    final TokenType tokenType = isNumericLiteral(value) ? TokenType.FLOAT_LITERAL : TokenType.VARIABLE_IDENTIFIER;
                    tokenList.add(new Token(tokenType, value));
                    variableIdentifier = new StringBuilder();
                }
                recordingIdentifierOrLiteral = false;
                tokenList.add(construct.toToken());
            } else {
                throw new IllegalArgumentException();
            }
        }
        if (recordingIdentifierOrLiteral) {
            final String value = variableIdentifier.toString();
            final TokenType tokenType = isNumericLiteral(value) ? TokenType.FLOAT_LITERAL : TokenType.VARIABLE_IDENTIFIER;
            tokenList.add(new Token(tokenType, value));
        }
        tokenList.add(new Token(TokenType.EOF, null));
        return tokenList;
    }

    private static boolean isNumericLiteral(final String expression) {
        return expression.matches("\\d?\\.?\\d+");
    }
}
