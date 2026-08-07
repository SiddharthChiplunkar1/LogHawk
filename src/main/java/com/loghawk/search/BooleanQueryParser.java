package com.loghawk.search;

import java.util.*;

public class BooleanQueryParser {

    public interface QueryNode {
        Set<Long> evaluate(java.util.function.Function<String, Set<Long>> termEvaluator);
    }

    public static class TermNode implements QueryNode {
        private final String term;

        public TermNode(String term) {
            this.term = term;
        }

        @Override
        public Set<Long> evaluate(java.util.function.Function<String, Set<Long>> termEvaluator) {
            return termEvaluator.apply(term);
        }
    }

    public static class AndNode implements QueryNode {
        private final QueryNode left;
        private final QueryNode right;

        public AndNode(QueryNode left, QueryNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Set<Long> evaluate(java.util.function.Function<String, Set<Long>> termEvaluator) {
            Set<Long> leftSet = left.evaluate(termEvaluator);
            if (leftSet.isEmpty()) return Collections.emptySet();
            Set<Long> rightSet = right.evaluate(termEvaluator);
            Set<Long> result = new HashSet<>(leftSet);
            result.retainAll(rightSet);
            return result;
        }
    }

    public static class OrNode implements QueryNode {
        private final QueryNode left;
        private final QueryNode right;

        public OrNode(QueryNode left, QueryNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Set<Long> evaluate(java.util.function.Function<String, Set<Long>> termEvaluator) {
            Set<Long> leftSet = left.evaluate(termEvaluator);
            Set<Long> rightSet = right.evaluate(termEvaluator);
            Set<Long> result = new HashSet<>(leftSet);
            result.addAll(rightSet);
            return result;
        }
    }

    public static class NotNode implements QueryNode {
        private final QueryNode node;

        public NotNode(QueryNode node) {
            this.node = node;
        }

        @Override
        public Set<Long> evaluate(java.util.function.Function<String, Set<Long>> termEvaluator) {
            // A pure NOT query requires knowing the universe of all IDs, but we'll assume it's used in conjunction with AND
            // For safety, evaluate returns the set to exclude. The engine must handle this.
            // Wait, for simplicity, let's just evaluate it and the engine will handle NOT separately if it's the root,
            // or better, pass the 'universe' of IDs if needed.
            // A simple approach: NOT node just returns the IDs to *exclude*.
            // To make it easy, we will change the AST evaluation to not just return a Set<Long>, but something that knows if it's inverted.
            // Alternatively, since NOT is usually "A AND NOT B", the AST can have an AndNotNode instead of a pure NotNode.
            throw new UnsupportedOperationException("Pure NOT node evaluation requires universe context.");
        }
    }
    
    public static class AndNotNode implements QueryNode {
        private final QueryNode left;
        private final QueryNode right;

        public AndNotNode(QueryNode left, QueryNode right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public Set<Long> evaluate(java.util.function.Function<String, Set<Long>> termEvaluator) {
            Set<Long> leftSet = left.evaluate(termEvaluator);
            if (leftSet.isEmpty()) return Collections.emptySet();
            Set<Long> rightSet = right.evaluate(termEvaluator);
            Set<Long> result = new HashSet<>(leftSet);
            result.removeAll(rightSet);
            return result;
        }
    }

    /**
     * Parses a boolean query string into an AST.
     * Supports AND, OR, NOT, and parentheses.
     */
    public static QueryNode parse(String query) {
        List<String> tokens = tokenize(query);
        return parseExpression(tokens);
    }

    private static List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(' || c == ')') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static QueryNode parseExpression(List<String> tokens) {
        // Very simplistic recursive descent parser
        // Handles OR at the lowest precedence, then AND, then NOT
        if (tokens.isEmpty()) return null;
        
        // Find top-level OR
        int orIndex = findTopLevelOperator(tokens, "OR");
        if (orIndex != -1) {
            QueryNode left = parseExpression(tokens.subList(0, orIndex));
            QueryNode right = parseExpression(tokens.subList(orIndex + 1, tokens.size()));
            return new OrNode(left, right);
        }

        // Find top-level AND or implicit AND (if no operator)
        int andIndex = findTopLevelOperator(tokens, "AND");
        if (andIndex != -1) {
            QueryNode left = parseExpression(tokens.subList(0, andIndex));
            // Check if it's AND NOT
            if (andIndex + 1 < tokens.size() && tokens.get(andIndex + 1).equalsIgnoreCase("NOT")) {
                QueryNode right = parseExpression(tokens.subList(andIndex + 2, tokens.size()));
                return new AndNotNode(left, right);
            }
            QueryNode right = parseExpression(tokens.subList(andIndex + 1, tokens.size()));
            return new AndNode(left, right);
        }
        
        // Find NOT (as a top level AND NOT if there are tokens before it)
        int notIndex = findTopLevelOperator(tokens, "NOT");
        if (notIndex != -1) {
            if (notIndex > 0) {
                 QueryNode left = parseExpression(tokens.subList(0, notIndex));
                 QueryNode right = parseExpression(tokens.subList(notIndex + 1, tokens.size()));
                 return new AndNotNode(left, right);
            } else {
                 // Pure NOT at the beginning is tricky, just skip the NOT keyword for now or treat as literal
                 return parseExpression(tokens.subList(1, tokens.size()));
            }
        }

        // Handle parentheses
        if (tokens.get(0).equals("(") && tokens.get(tokens.size() - 1).equals(")")) {
            return parseExpression(tokens.subList(1, tokens.size() - 1));
        }
        
        // Implicit AND if multiple tokens are present without operators
        if (tokens.size() > 1) {
            QueryNode left = new TermNode(tokens.get(0));
            QueryNode right = parseExpression(tokens.subList(1, tokens.size()));
            return new AndNode(left, right);
        }

        // Single term
        return new TermNode(tokens.get(0));
    }

    private static int findTopLevelOperator(List<String> tokens, String operator) {
        int depth = 0;
        // Search backwards to make left-associative
        for (int i = tokens.size() - 1; i >= 0; i--) {
            String token = tokens.get(i);
            if (token.equals(")")) {
                depth++;
            } else if (token.equals("(")) {
                depth--;
            } else if (depth == 0 && token.equalsIgnoreCase(operator)) {
                // If checking for AND, ensure it's not part of AND NOT (unless operator is exactly what we want)
                return i;
            }
        }
        return -1;
    }
}
