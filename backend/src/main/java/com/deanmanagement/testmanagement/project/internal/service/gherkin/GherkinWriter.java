package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import io.cucumber.gherkin.GherkinDialect;
import io.cucumber.gherkin.GherkinDialectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes test cases as one {@code .feature} file (PRD-040 §3.4). Pure. Chosen so that importing the
 * output again changes nothing; what cannot round-trip (expected results, preconditions that are
 * not steps) is written as comments, which the import drops.
 */
public final class GherkinWriter {

    private static final GherkinDialectProvider DIALECTS = new GherkinDialectProvider();
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String CATCH_ALL_KEYWORD = "* ";
    private static final String INDENT = "  ";
    private static final String DOC_STRING = "\"\"\"";
    private static final String ALTERNATIVE_DOC_STRING = "```";
    private static final Pattern SUBSTITUTION = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
    private static final Pattern NUMBERED_SET_NAME = Pattern.compile("^(.*) #\\d+$");
    private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\R\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private GherkinWriter() {
    }

    public record ExportCase(String key, String title, String description, String preconditions,
                             Priority priority, Set<String> labels, List<TestStepRequest> steps,
                             List<SaveParameterSetRequest> parameterSets) {
    }

    public record RuleBlock(String name, List<ExportCase> cases) {
    }

    /** {@code notes} become header comments, e.g. that deeper folders were flattened. */
    public record FeatureDoc(String name, List<ExportCase> cases, List<RuleBlock> rules, List<String> notes) {
    }

    public static String write(FeatureDoc feature) {
        List<ExportCase> all = Stream.concat(feature.cases().stream(),
                feature.rules().stream().flatMap(r -> r.cases().stream())).toList();
        GherkinDialect dialect = dialectFor(all);
        String featureBackground = commonBackground(feature.cases(), all, dialect);

        Out out = new Out();
        if (!dialect.getLanguage().equals(DEFAULT_LANGUAGE)) {
            out.line(0, "# language: " + dialect.getLanguage());
        }
        feature.notes().forEach(note -> out.line(0, "# " + note));
        out.line(0, dialect.getFeatureKeywords().getFirst() + ": " + oneLine(feature.name()));
        writeBackground(out, 1, featureBackground, dialect);
        for (ExportCase c : feature.cases()) {
            writeScenario(out, 1, c, featureBackground, dialect);
        }
        for (RuleBlock rule : feature.rules()) {
            out.blank();
            // The last rule keyword: German lists the English "Rule" first, then "Regel".
            out.line(1, dialect.getRuleKeywords().getLast() + ": " + oneLine(rule.name()));
            String ruleBackground = ruleBackground(rule.cases(), featureBackground, dialect);
            String inherited = join(featureBackground, ruleBackground);
            writeBackground(out, 2, ruleBackground, dialect);
            for (ExportCase c : rule.cases()) {
                writeScenario(out, 2, c, inherited, dialect);
            }
        }
        return out.toString();
    }

    // ---- language ----------------------------------------------------------------------------

    /**
     * English, unless English keywords do not fit every step and one other language does: then
     * that language, so a German suite exports as German.
     */
    private static GherkinDialect dialectFor(List<ExportCase> cases) {
        List<String> actions = cases.stream().flatMap(c -> c.steps().stream()).map(TestStepRequest::action).toList();
        GherkinDialect english = DIALECTS.getDefaultDialect();
        if (actions.isEmpty() || actions.stream().allMatch(a -> isKeywordStep(a, english))) {
            return english;
        }
        return DIALECTS.getLanguages().stream().sorted()
                .map(language -> DIALECTS.getDialect(language).orElseThrow())
                .filter(d -> actions.stream().allMatch(a -> isKeywordStep(a, d)))
                .findFirst()
                .orElse(english);
    }

    /** Keywords that end in a letter need a space after them; some languages' keywords do not. */
    private static boolean isKeywordStep(String action, GherkinDialect dialect) {
        return action != null && dialect.getStepKeywords().stream()
                .filter(k -> !k.equals(CATCH_ALL_KEYWORD))
                .anyMatch(k -> action.startsWith(k) && action.length() > k.length());
    }

    // ---- backgrounds -------------------------------------------------------------------------

    /**
     * The feature's direct cases all share it, every rule case starts with it, and it is made of
     * steps. Otherwise there is no feature background and preconditions go per scenario.
     */
    private static String commonBackground(List<ExportCase> direct, List<ExportCase> all, GherkinDialect dialect) {
        if (direct.isEmpty()) {
            return null;
        }
        String candidate = direct.getFirst().preconditions();
        if (candidate == null || candidate.isBlank() || !isSteps(candidate, dialect)
                || direct.stream().anyMatch(c -> !candidate.equals(c.preconditions()))
                || all.stream().anyMatch(c -> !startsWithLines(c.preconditions(), candidate))) {
            return null;
        }
        return candidate;
    }

    private static String ruleBackground(List<ExportCase> cases, String featureBackground, GherkinDialect dialect) {
        if (cases.isEmpty()) {
            return null;
        }
        String candidate = remainder(cases.getFirst().preconditions(), featureBackground);
        if (candidate == null || candidate.isBlank() || !isSteps(candidate, dialect)
                || cases.stream().anyMatch(c -> !candidate.equals(remainder(c.preconditions(), featureBackground)))) {
            return null;
        }
        return candidate;
    }

    private static boolean isSteps(String text, GherkinDialect dialect) {
        return text.lines().allMatch(line -> isKeywordStep(line, dialect));
    }

    private static boolean startsWithLines(String text, String prefix) {
        return text != null && (text.equals(prefix) || text.startsWith(prefix + "\n"));
    }

    /** What is left of a case's preconditions once the inherited background is taken off. */
    private static String remainder(String preconditions, String inherited) {
        if (preconditions == null || inherited == null) {
            return preconditions;
        }
        if (preconditions.equals(inherited)) {
            return null;
        }
        return preconditions.substring(inherited.length() + 1);
    }

    private static String join(String first, String second) {
        if (first == null) {
            return second;
        }
        return second == null ? first : first + "\n" + second;
    }

    private static void writeBackground(Out out, int depth, String background, GherkinDialect dialect) {
        if (background == null) {
            return;
        }
        out.blank();
        out.line(depth, dialect.getBackgroundKeywords().getFirst() + ":");
        background.lines().forEach(line -> out.line(depth + 1, line));
    }

    // ---- scenarios ---------------------------------------------------------------------------

    private static void writeScenario(Out out, int depth, ExportCase c, String inheritedBackground,
                                      GherkinDialect dialect) {
        boolean outline = !c.parameterSets().isEmpty();
        out.blank();
        String own = remainder(c.preconditions(), inheritedBackground);
        if (own != null && !own.isBlank()) {
            commentLines(out, depth, "preconditions: ", own);
        }
        out.line(depth, tagLine(c, out, depth));
        // The last scenario keyword: the first is "Example" in English, "Beispiel" in German.
        String keyword = outline ? dialect.getScenarioOutlineKeywords().getFirst() : dialect.getScenarioKeywords().getLast();
        out.line(depth, keyword + ": " + oneLine(c.title()));
        if (c.description() != null && !c.description().isBlank()) {
            c.description().lines().forEach(line -> out.line(depth + 1, line));
        }
        Set<String> parameters = new LinkedHashSet<>();
        c.parameterSets().forEach(set -> parameters.addAll(set.values().keySet()));
        for (TestStepRequest step : c.steps()) {
            writeStep(out, depth + 1, step, parameters, dialect);
        }
        if (outline) {
            writeExamples(out, depth + 1, c.parameterSets(), dialect);
        }
    }

    /**
     * {@code @tm:<KEY>} first, then {@code @priority:} unless it is the default, then labels.
     * A label with whitespace cannot be a tag: it is written with dashes and noted.
     */
    private static String tagLine(ExportCase c, Out out, int depth) {
        List<String> tags = new ArrayList<>();
        tags.add("@" + GherkinFileParser.KEY_TAG_PREFIX + c.key());
        if (c.priority() != null && c.priority() != Priority.MEDIUM) {
            tags.add("@priority:" + c.priority().name().toLowerCase());
        }
        for (String label : c.labels().stream().sorted().toList()) {
            String tag = WHITESPACE.matcher(label.trim()).replaceAll("-");
            if (!tag.equals(label)) {
                out.line(depth, "# label \"" + oneLine(label) + "\" written as @" + tag);
            }
            tags.add("@" + tag);
        }
        return String.join(" ", tags);
    }

    /**
     * A step already written as Gherkin keeps its keyword; any other is written with {@code *}.
     * A non-empty expected result has no Gherkin form and becomes a comment.
     */
    private static void writeStep(Out out, int depth, TestStepRequest step, Set<String> parameters,
                                  GherkinDialect dialect) {
        String action = toPlaceholders(step.action(), parameters);
        out.line(depth, isKeywordStep(step.action(), dialect) ? oneLine(action) : CATCH_ALL_KEYWORD + oneLine(action));
        if (step.testData() != null && !step.testData().isEmpty()) {
            String data = toPlaceholders(step.testData(), parameters);
            if (isTable(data)) {
                data.lines().forEach(line -> out.line(depth + 1, line.strip()));
            } else {
                writeDocString(out, depth + 1, data);
            }
        }
        if (step.expectedResult() != null && !step.expectedResult().isBlank()) {
            commentLines(out, depth, "expected: ", step.expectedResult());
        }
    }

    /** Only what the import itself writes: pipe rows. Anything else goes out as a doc string. */
    private static boolean isTable(String data) {
        return data.lines().allMatch(line -> {
            String s = line.strip();
            return s.length() >= 2 && s.startsWith("|") && s.endsWith("|") && !s.endsWith("\\|");
        });
    }

    private static void writeDocString(Out out, int depth, String data) {
        String fence = data.contains(DOC_STRING) ? ALTERNATIVE_DOC_STRING : DOC_STRING;
        out.line(depth, fence);
        data.lines().forEach(line -> out.line(depth, line));
        out.line(depth, fence);
    }

    /**
     * Sets named "{@code <name> #<n>}" by the import go back into Examples blocks of that name, so a
     * re-import names them the same; "Example" is the name of an unnamed block.
     */
    private static void writeExamples(Out out, int depth, List<SaveParameterSetRequest> sets, GherkinDialect dialect) {
        String currentBlock = null;
        List<SaveParameterSetRequest> block = new ArrayList<>();
        for (SaveParameterSetRequest set : sets) {
            String name = blockName(set.name());
            if (!name.equals(currentBlock) && !block.isEmpty()) {
                writeExamplesBlock(out, depth, currentBlock, block, dialect);
                block = new ArrayList<>();
            }
            currentBlock = name;
            block.add(set);
        }
        writeExamplesBlock(out, depth, currentBlock, block, dialect);
    }

    private static String blockName(String setName) {
        Matcher matcher = NUMBERED_SET_NAME.matcher(setName);
        return matcher.matches() ? matcher.group(1) : setName;
    }

    /** Header is the union of the block's keys; a set without a key gets an empty cell. */
    private static void writeExamplesBlock(Out out, int depth, String name, List<SaveParameterSetRequest> sets,
                                           GherkinDialect dialect) {
        Set<String> header = new LinkedHashSet<>();
        sets.forEach(s -> header.addAll(s.values().keySet()));
        out.blank();
        String title = GherkinFileParser.DEFAULT_EXAMPLES_NAME.equals(name) ? "" : " " + oneLine(name);
        out.line(depth, dialect.getExamplesKeywords().getFirst() + ":" + title);
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(header));
        for (SaveParameterSetRequest set : sets) {
            Map<String, String> values = new LinkedHashMap<>(set.values());
            rows.add(header.stream().map(k -> values.getOrDefault(k, "")).toList());
        }
        writeTable(out, depth + 1, rows);
    }

    /** Columns padded to a common width, as Cucumber's own formatter writes them: files are diffed. */
    private static void writeTable(Out out, int depth, List<List<String>> rows) {
        List<List<String>> escaped = rows.stream()
                .map(row -> row.stream()
                        .map(c -> c.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n"))
                        .toList())
                .toList();
        int columns = escaped.getFirst().size();
        int[] widths = new int[columns];
        for (List<String> row : escaped) {
            for (int i = 0; i < columns; i++) {
                widths[i] = Math.max(widths[i], row.get(i).length());
            }
        }
        for (List<String> row : escaped) {
            StringBuilder line = new StringBuilder("|");
            for (int i = 0; i < columns; i++) {
                line.append(' ').append(row.get(i)).append(" ".repeat(widths[i] - row.get(i).length())).append(" |");
            }
            out.line(depth, line.toString());
        }
    }

    /** {@code {x}} → {@code <x>} for the case's parameters; other braces are not placeholders. */
    private static String toPlaceholders(String text, Set<String> parameters) {
        return SUBSTITUTION.matcher(text).replaceAll(m -> Matcher.quoteReplacement(
                parameters.contains(m.group(1)) ? "<" + m.group(1) + ">" : m.group()));
    }

    private static void commentLines(Out out, int depth, String label, String text) {
        List<String> lines = text.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            out.line(depth, "# " + (i == 0 ? label : " ".repeat(label.length())) + lines.get(i));
        }
    }

    /** A step, title or name is one line in Gherkin; other whitespace is kept as it is. */
    private static String oneLine(String text) {
        return text == null ? "" : LINE_BREAKS.matcher(text.strip()).replaceAll(" ");
    }

    private static final class Out {
        private final StringBuilder text = new StringBuilder();

        void line(int depth, String line) {
            text.append(INDENT.repeat(depth)).append(line).append('\n');
        }

        void blank() {
            text.append('\n');
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
