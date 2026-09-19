package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinFileParser.Scenario;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinWriter.ExportCase;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinWriter.FeatureDoc;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinWriter.RuleBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The PRD-040 §3.4 export, checked by reading its output back with the importer's parser. */
class GherkinWriterTest {

    private static TestStepRequest step(String action) {
        return new TestStepRequest(action, null, null);
    }

    private static ExportCase exportCase(String key, String title, List<TestStepRequest> steps) {
        return new ExportCase(key, title, null, null, Priority.MEDIUM, Set.of(), steps, List.of());
    }

    private static String write(ExportCase... cases) {
        return GherkinWriter.write(new FeatureDoc("Login", List.of(cases), List.of(), List.of()));
    }

    private static List<Scenario> readBack(String feature) {
        return GherkinFileParser.parse("login.feature", feature).scenarios();
    }

    @Nested
    class RoundTrip {

        @Test
        void aGherkinCaseReadsBackAsTheSameCase() {
            ExportCase original = new ExportCase("P-7", "Valid credentials", "Checks the happy path.\n  With detail.",
                    "Given the app is running", Priority.HIGH, Set.of("smoke", "auth"),
                    List.of(new TestStepRequest("When I post", null, "{\"a\": 1}"),
                            new TestStepRequest("Given users", null, "| name | role |\n| Alice | a\\|b |"),
                            step("Then it works")),
                    List.of());

            Scenario back = readBack(write(original)).getFirst();

            assertThat(back.key()).isEqualTo("P-7");
            assertThat(back.title()).isEqualTo(original.title());
            assertThat(back.description()).isEqualTo(original.description());
            assertThat(back.preconditions()).isEqualTo(original.preconditions());
            assertThat(back.priority()).isEqualTo(Priority.HIGH);
            assertThat(back.labels()).isEqualTo(original.labels());
            assertThat(back.steps()).isEqualTo(original.steps());
        }

        @Test
        void anOutlineReadsBackWithTheSameSetsAndPlaceholders() {
            List<SaveParameterSetRequest> sets = List.of(
                    new SaveParameterSetRequest("Small #1", Map.of("amount", "1", "currency", "EUR"), 0),
                    new SaveParameterSetRequest("Example #1", Map.of("amount", "2", "currency", "JPY"), 1),
                    new SaveParameterSetRequest("Example #2", Map.of("amount", "3|4", "currency", "CHF"), 2));
            ExportCase original = new ExportCase("P-8", "Convert", null, null, Priority.MEDIUM, Set.of(),
                    List.of(step("When I convert {amount} to {currency} in {unknown}")), sets);

            Scenario back = readBack(write(original)).getFirst();

            assertThat(back.steps()).isEqualTo(original.steps());
            assertThat(back.parameterSets()).isEqualTo(sets);
        }

        @Test
        void featureAndRuleBackgroundsReadBackAsTheSamePreconditions() {
            ExportCase direct = new ExportCase("P-1", "Direct", null, "Given the app", Priority.MEDIUM, Set.of(),
                    List.of(step("When a")), List.of());
            ExportCase inRule = new ExportCase("P-2", "In rule", null, "Given the app\nGiven a locked account",
                    Priority.MEDIUM, Set.of(), List.of(step("When b")), List.of());

            String feature = GherkinWriter.write(new FeatureDoc("Login", List.of(direct),
                    List.of(new RuleBlock("Lockout", List.of(inRule))), List.of()));

            assertThat(feature).contains("Background:");
            assertThat(readBack(feature)).extracting(Scenario::preconditions)
                    .containsExactly(direct.preconditions(), inRule.preconditions());
            assertThat(readBack(feature)).extracting(Scenario::folderPath)
                    .containsExactly(List.of("Login"), List.of("Login", "Lockout"));
        }

        @Test
        void aGermanSuiteIsWrittenInGerman() {
            String feature = write(exportCase("P-1", "Gültige Daten",
                    List.of(step("Angenommen ein Benutzer"), step("Wenn er sich anmeldet"))));

            assertThat(feature).startsWith("# language: de\nFunktionalität: Login\n");
            assertThat(readBack(feature).getFirst().steps()).extracting(TestStepRequest::action)
                    .containsExactly("Angenommen ein Benutzer", "Wenn er sich anmeldet");
        }
    }

    @Nested
    class Lossy {

        @Test
        void aClassicStepIsWrittenWithTheCatchAllKeywordAndItsExpectedResultAsAComment() {
            String feature = write(exportCase("P-1", "Classic",
                    List.of(new TestStepRequest("Open the page", "Page loads\nquickly", null))));

            assertThat(feature).contains("""
                        * Open the page
                        # expected: Page loads
                        #           quickly
                    """);
        }

        @Test
        void preconditionsThatAreNotStepsBecomeAComment() {
            ExportCase c = new ExportCase("P-1", "S", null, "A logged-in admin", Priority.MEDIUM, Set.of(),
                    List.of(step("Given a")), List.of());

            String feature = write(c);

            assertThat(feature).doesNotContain("Background:").contains("# preconditions: A logged-in admin");
        }

        @Test
        void aLabelWithSpacesIsWrittenWithDashesAndNoted() {
            ExportCase c = new ExportCase("P-1", "S", null, null, Priority.MEDIUM, Set.of("needs review"),
                    List.of(step("Given a")), List.of());

            String feature = write(c);

            assertThat(feature).contains("# label \"needs review\" written as @needs-review")
                    .contains("@tm:P-1 @needs-review");
        }

        @Test
        void aMultiLineTitleIsJoinedIntoOneLine() {
            assertThat(write(exportCase("P-1", "First\nsecond", List.of(step("Given a")))))
                    .contains("Scenario: First second");
        }
    }
}
