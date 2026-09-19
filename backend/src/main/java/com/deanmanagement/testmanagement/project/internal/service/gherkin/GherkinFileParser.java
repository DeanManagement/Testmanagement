package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Background;
import io.cucumber.messages.types.DataTable;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns one {@code .feature} file into test-case shaped scenarios (PRD-040 §1, §3.3). Pure: no
 * database, so it serves the import, the form's preview and the tests alike.
 *
 * <p>Walks the parser's document tree rather than its expanded pickles: an outline plus its examples
 * is one case with parameter sets, not one case per row.
 */
public final class GherkinFileParser {

    public static final String KEY_TAG_PREFIX = "tm:";
    private static final String PRIORITY_TAG_PREFIX = "priority:";
    private static final String DEFAULT_EXAMPLES_NAME = "Example";
    /** What ParameterSubstitutor accepts inside {@code {…}}; anything else is normalised to '_'. */
    private static final Pattern NOT_A_PARAMETER_CHAR = Pattern.compile("[^A-Za-z0-9_.-]");
    private static final Pattern OUTLINE_PLACEHOLDER = Pattern.compile("<([^<>]+)>");

    private GherkinFileParser() {
    }

    /** One scenario, ready to become a case. {@code problems} non-empty means it must not be imported. */
    public record Scenario(String location, List<String> folderPath, String key, String title,
                           String description, String preconditions, Priority priority, Set<String> labels,
                           List<TestStepRequest> steps, List<SaveParameterSetRequest> parameterSets,
                           List<String> problems, List<String> warnings) {
    }

    public record ParsedFile(List<Scenario> scenarios, List<String> warnings) {
    }

    /**
     * @throws IllegalArgumentException when the file is not valid Gherkin; the message names the
     *                                  file, line and what the parser expected
     */
    public static ParsedFile parse(String fileName, String text) {
        GherkinParser parser = GherkinParser.builder()
                .includeSource(false).includePickles(false).includeGherkinDocument(true).build();
        List<Envelope> envelopes = parser.parse(fileName, text.getBytes(StandardCharsets.UTF_8)).toList();

        String errors = envelopes.stream()
                .flatMap(e -> e.getParseError().stream())
                .map(error -> error.getMessage())
                .collect(Collectors.joining("; "));
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(fileName + ": " + errors);
        }
        GherkinDocument document = envelopes.stream()
                .flatMap(e -> e.getGherkinDocument().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(fileName + ": not a Gherkin document"));

        List<String> warnings = new ArrayList<>();
        Feature feature = document.getFeature().orElse(null);
        if (feature == null) {
            warnings.add(fileName + ": no Feature, nothing to import");
            return new ParsedFile(List.of(), warnings);
        }
        if (!document.getComments().isEmpty()) {
            warnings.add(fileName + ": " + document.getComments().size() + " comment line(s) not imported");
        }
        if (!feature.getDescription().isBlank()) {
            warnings.add(fileName + ": the Feature description is not imported");
        }
        return new ParsedFile(new FileWalker(fileName, feature).walk(), warnings);
    }

    /** Holds what one file's scenarios inherit: the feature, its tags and its background. */
    private static final class FileWalker {

        private final String fileName;
        private final Feature feature;
        private final String featureFolder;
        private final List<Scenario> scenarios = new ArrayList<>();

        FileWalker(String fileName, Feature feature) {
            this.fileName = fileName;
            this.feature = feature;
            this.featureFolder = feature.getName().isBlank() ? baseName(fileName) : feature.getName().trim();
        }

        List<Scenario> walk() {
            List<Step> featureBackground = new ArrayList<>();
            for (FeatureChild child : feature.getChildren()) {
                child.getBackground().ifPresent(b -> featureBackground.addAll(b.getSteps()));
                child.getScenario().ifPresent(s ->
                        scenarios.add(toScenario(s, List.of(featureFolder), feature.getTags(), featureBackground)));
                child.getRule().ifPresent(rule -> walkRule(rule, featureBackground));
            }
            return scenarios;
        }

        private void walkRule(Rule rule, List<Step> featureBackground) {
            List<String> folders = rule.getName().isBlank()
                    ? List.of(featureFolder)
                    : List.of(featureFolder, rule.getName().trim());
            List<Tag> tags = Stream.concat(feature.getTags().stream(), rule.getTags().stream()).toList();
            List<Step> background = new ArrayList<>(featureBackground);
            for (RuleChild child : rule.getChildren()) {
                child.getBackground().map(Background::getSteps).ifPresent(background::addAll);
                child.getScenario().ifPresent(s -> scenarios.add(toScenario(s, folders, tags, background)));
            }
        }

        private Scenario toScenario(io.cucumber.messages.types.Scenario source, List<String> folders,
                                    List<Tag> inheritedTags, List<Step> background) {
            String location = fileName + ":" + source.getLocation().getLine();
            List<String> problems = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            TagReading tags = readTags(inheritedTags, source.getTags(), problems, warnings);
            Map<String, String> placeholders = placeholderNames(source.getExamples(), warnings);
            List<SaveParameterSetRequest> sets = parameterSets(source.getExamples(), placeholders, problems);
            List<TestStepRequest> steps = source.getSteps().stream()
                    .map(step -> new TestStepRequest(
                            rewritePlaceholders(step.getKeyword() + step.getText(), placeholders),
                            null,
                            rewritePlaceholders(argumentOf(step), placeholders)))
                    .toList();
            if (source.getName().isBlank()) {
                problems.add("the scenario has no name");
            }
            if (source.getExamples().stream().anyMatch(e -> !e.getTags().isEmpty())) {
                warnings.add("tags on Examples are not imported");
            }
            return new Scenario(location, folders, tags.key(), source.getName().trim(),
                    emptyToNull(stripIndent(source.getDescription())), preconditionsOf(background),
                    tags.priority(), tags.labels(), steps, sets, problems, warnings);
        }
    }

    private record TagReading(String key, Priority priority, Set<String> labels) {
    }

    /**
     * Feature and rule tags are inherited as labels. {@code @tm:} and {@code @priority:} are consumed,
     * and only count on the scenario itself: a key on a feature would claim every scenario in it.
     */
    private static TagReading readTags(List<Tag> inherited, List<Tag> own, List<String> problems,
                                       List<String> warnings) {
        Set<String> labels = new LinkedHashSet<>();
        for (Tag tag : inherited) {
            String name = tag.getName().substring(1);
            if (name.startsWith(KEY_TAG_PREFIX) || name.startsWith(PRIORITY_TAG_PREFIX)) {
                warnings.add("@" + name + " on a Feature or Rule is ignored; put it on the scenario");
            } else {
                labels.add(name);
            }
        }
        List<String> keys = new ArrayList<>();
        Priority priority = null;
        for (Tag tag : own) {
            String name = tag.getName().substring(1);
            if (name.startsWith(KEY_TAG_PREFIX)) {
                keys.add(name.substring(KEY_TAG_PREFIX.length()));
            } else if (name.startsWith(PRIORITY_TAG_PREFIX)) {
                priority = parsePriority(name.substring(PRIORITY_TAG_PREFIX.length()), problems);
            } else {
                labels.add(name);
            }
        }
        if (keys.size() > 1) {
            problems.add("more than one @" + KEY_TAG_PREFIX + " tag: " + String.join(", ", keys));
        }
        String key = keys.isEmpty() || keys.getFirst().isBlank() ? null : keys.getFirst();
        return new TagReading(key, priority, labels);
    }

    private static Priority parsePriority(String value, List<String> problems) {
        try {
            return Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            problems.add("invalid @" + PRIORITY_TAG_PREFIX + value + " (one of LOW, MEDIUM, HIGH, CRITICAL)");
            return null;
        }
    }

    /** Examples header → the name used in {@code {…}} and as the set's value key. */
    private static Map<String, String> placeholderNames(List<Examples> examples, List<String> warnings) {
        Map<String, String> names = new LinkedHashMap<>();
        for (Examples block : examples) {
            for (TableCell cell : block.getTableHeader().map(TableRow::getCells).orElse(List.of())) {
                String header = cell.getValue();
                String normalised = NOT_A_PARAMETER_CHAR.matcher(header.trim()).replaceAll("_");
                if (!normalised.equals(header) && !names.containsKey(header)) {
                    warnings.add("parameter <" + header + "> renamed to {" + normalised + "}");
                }
                names.put(header, normalised);
            }
        }
        return names;
    }

    /**
     * One set per example row, named "{@code <Examples name or 'Example'> #<n>}". Numbering continues
     * across blocks of the same name, so two unnamed blocks do not collide.
     */
    private static List<SaveParameterSetRequest> parameterSets(List<Examples> examples,
                                                               Map<String, String> placeholders,
                                                               List<String> problems) {
        List<SaveParameterSetRequest> sets = new ArrayList<>();
        Map<String, Integer> rowsPerName = new LinkedHashMap<>();
        for (Examples block : examples) {
            List<TableCell> header = block.getTableHeader().map(TableRow::getCells).orElse(List.of());
            String name = block.getName().isBlank() ? DEFAULT_EXAMPLES_NAME : block.getName().trim();
            for (TableRow row : block.getTableBody()) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < header.size() && i < row.getCells().size(); i++) {
                    values.put(placeholders.get(header.get(i).getValue()), row.getCells().get(i).getValue());
                }
                int number = rowsPerName.merge(name, 1, Integer::sum);
                sets.add(new SaveParameterSetRequest(name + " #" + number, values, sets.size()));
            }
        }
        if (sets.stream().map(SaveParameterSetRequest::values).anyMatch(v -> v.containsKey(""))) {
            problems.add("an Examples column has no header");
        }
        return sets;
    }

    /** {@code <x>} → {@code {x}} for columns of the examples; anything else stays literal. */
    private static String rewritePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null || placeholders.isEmpty()) {
            return text;
        }
        Matcher matcher = OUTLINE_PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = placeholders.get(matcher.group(1));
            String replacement = name == null ? matcher.group() : "{" + name + "}";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** A doc string's content, or a data table as pipe rows; null when the step has neither. */
    static String argumentOf(Step step) {
        if (step.getDocString().isPresent()) {
            return step.getDocString().get().getContent();
        }
        return step.getDataTable().map(GherkinFileParser::renderTable).orElse(null);
    }

    private static String renderTable(DataTable table) {
        return table.getRows().stream()
                .map(row -> row.getCells().stream()
                        .map(cell -> cell.getValue().replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n"))
                        .collect(Collectors.joining(" | ", "| ", " |")))
                .collect(Collectors.joining("\n"));
    }

    /** One line per background step, with its doc string or table below it. */
    private static String preconditionsOf(List<Step> background) {
        if (background.isEmpty()) {
            return null;
        }
        return background.stream()
                .map(step -> {
                    String line = step.getKeyword() + step.getText();
                    String argument = argumentOf(step);
                    return argument == null ? line : line + "\n" + argument;
                })
                .collect(Collectors.joining("\n"));
    }

    /** The parser keeps a description's source indentation; the case should not. */
    private static String stripIndent(String description) {
        return description == null ? null : description.stripIndent().strip();
    }

    private static String baseName(String fileName) {
        String name = fileName.substring(fileName.lastIndexOf('/') + 1);
        return name.endsWith(".feature") ? name.substring(0, name.length() - ".feature".length()) : name;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
