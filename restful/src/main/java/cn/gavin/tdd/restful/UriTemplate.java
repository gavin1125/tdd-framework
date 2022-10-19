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
    private final Pattern pattern;
    private final PathVariables pathVariables = new PathVariables();

    private int variableGroupStartFrom;

    public PathTemplate(String template) {
        pattern = Pattern.compile(group(pathVariables.template(template)) + "(/.*)?");
        variableGroupStartFrom = 2;
    }

    @Override
    public Optional<MatchResult> match(String path) {
        Matcher matcher = pattern.matcher(path);
        if (!matcher.matches()) return Optional.empty();
        return Optional.of(new PathMatchResult(matcher, pathVariables));
    }

    class PathVariables implements Comparable<PathVariables> {
        private static final String LeftBracket = "\\{";
        private static final String RightBracket = "}";
        private static final String VariableName = "\\w[\\w\\.-]*";
        private static final String NonBrackets = "[^\\{}]+";

        private static Pattern variable = Pattern.compile(LeftBracket + group(VariableName) +
                group(":" + group(NonBrackets)) + "?" + RightBracket);
        private static final int variableNameGroup = 1;
        private static final int variablePatternGroup = 3;

        private static String defaultVariablePattern = "([^/]+?)";

        private final List<String> variables = new ArrayList<>();
        private int specificPatternCount = 0;

        private String template(String template) {
            return variable.matcher(template).replaceAll(pathVariables::replace);
        }

        private String replace(java.util.regex.MatchResult result) {
            String name = result.group(variableNameGroup);
            String pattern = result.group(PathVariables.variablePatternGroup);

            if (variables.contains(name))
                throw new IllegalArgumentException("duplicate variable " + name);

            variables.add(name);
            if (pattern != null) {
                specificPatternCount++;
                return group(pattern);
            }
            return defaultVariablePattern;
        }

        private Map<String, String> extract(Matcher matcher) {
            Map<String, String> parameters = new HashMap<>();
            for (int i = 0; i < variables.size(); i++) {
                parameters.put(variables.get(i), matcher.group(variableGroupStartFrom + i));
            }
            return parameters;
        }

        @Override
        public int compareTo(PathVariables o) {
            if (variables.size() > o.variables.size()) return -1;
            if (variables.size() < o.variables.size()) return 1;
            return Integer.compare(o.specificPatternCount, specificPatternCount);
        }
    }

    class PathMatchResult implements MatchResult {
        private int matchLiteralCount;
        private PathVariables variables;
        private Matcher matcher;
        private Map<String, String> parameters;

        public PathMatchResult(Matcher matcher, PathVariables variables) {
            this.matcher = matcher;
            this.variables = variables;
            this.parameters = variables.extract(matcher);
            this.matchLiteralCount = matcher.group(1).length() - parameters.values().stream().map(String::length).reduce(0, (acc, n) -> acc + n);
        }

        @Override
        public int compareTo(MatchResult o) {
            PathMatchResult result = (PathMatchResult) o;
            if (matchLiteralCount > result.matchLiteralCount) return -1;
            if (matchLiteralCount < result.matchLiteralCount) return 1;
            return variables.compareTo(result.variables);
        }

        @Override
        public String getMatched() {
            return matcher.group(1);
        }

        @Override
        public String getRemaining() {
            return matcher.group(matcher.groupCount());
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
