package cn.gavin.tdd.restful;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

interface UriTemplate {
    interface MatchResult extends Comparable<MatchResult> {
        String getMatched();

        String getRemaining();

        Map<String, String> getMatchedPathParameters();
    }

    Optional<MatchResult> match(String path);
}

class PathTemplate implements UriTemplate {
    private static final String LeftBracket = "\\{";
    private static final String RightBracket = "}";
    private static final String VariableName = "\\w[\\w\\.-]*";
    private static final String NonBrackets = "[^\\{}]+";

    private static Pattern variable = Pattern.compile(LeftBracket + group(VariableName) +
            group(":" + group(NonBrackets)) + "?" + RightBracket);
    private static final int variableNameGroup = 1;
    private static final int variablePatternGroup = 3;

    private String defaultVariablePattern = "([^/]+?)";

    private final Pattern pattern;
    private final List<String> variables = new ArrayList<>();
    private int specificPatternCount = 0;
    private int variableGroupStartFrom;


    public PathTemplate(String template) {
        pattern = Pattern.compile(group(variable(template)) + "(/.*)?");
        variableGroupStartFrom = 2;
    }

    private String variable(String template) {
        return variable.matcher(template).replaceAll(result -> {
            String variableName = result.group(variableNameGroup);
            String pattern = result.group(variablePatternGroup);

            if (variables.contains(variableName))
                throw new IllegalArgumentException("duplicate variable " + variableName);

            variables.add(variableName);
            if (pattern != null) {
                specificPatternCount++;
                return group(pattern);
            }
            return defaultVariablePattern;
        });
    }

    @Override
    public Optional<MatchResult> match(String path) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) return Optional.empty();
        return Optional.of(new PathMatchResult(matcher));
    }

    class PathMatchResult implements MatchResult {
        private final int specificParameterCount;
        private int matchLiteralCount;
        private int count;
        private Matcher matcher;
        private Map<String, String> parameters = new HashMap<>();

        public PathMatchResult(Matcher matcher) {
            this.matcher = matcher;
            count = matcher.groupCount();
            this.matchLiteralCount = matcher.group(1).length();
            this.specificParameterCount = specificPatternCount;

            for (int i = 0; i < variables.size(); i++) {
                parameters.put(variables.get(i), matcher.group(variableGroupStartFrom + i));
                matchLiteralCount -= matcher.group(variableGroupStartFrom + i).length();
            }
        }

        @Override
        public int compareTo(MatchResult o) {
            PathMatchResult result = (PathMatchResult) o;
            if (matchLiteralCount > result.matchLiteralCount) return -1;
            if (matchLiteralCount < result.matchLiteralCount) return 1;
            if (parameters.size() > result.parameters.size()) return -1;
            if (parameters.size() < result.parameters.size()) return 1;
            if (specificParameterCount > result.specificParameterCount) return -1;
            if (specificParameterCount < result.specificParameterCount) return 1;
            return 0;
        }

        @Override
        public String getMatched() {
            return matcher.group(1);
        }

        @Override
        public String getRemaining() {
            return matcher.group(count);
        }

        @Override
        public Map<String, String> getMatchedPathParameters() {
            return parameters;
        }

    }

    private static String group(String pattern) {
        return "(" + pattern + ")";
    }
}
