package com.deanmanagement.testmanagement.project.internal.service.gherkin;

import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.parameter.SaveParameterSetRequest;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinFileParser.ParsedFile;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinFileParser.Scenario;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The PRD-040 §1 mapping, without a database. */
class GherkinFileParserTest {

    private static List<Scenario> scenarios(String text) {
        return GherkinFileParser.parse("login.feature", text).scenarios();
    }

    private static Scenario only(String text) {
        List<Scenario> all = scenarios(text);
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    @Nested
    class Structure {

        @Test
        void aFeatureBecomesTheFolderAndAScenarioACase() {
            Scenario scenario = only("""
                    Feature: Login
                      Scenario: Valid credentials
                        Given a registered user
                        When they sign in
                        Then the dashboard is shown
                    """);

            assertThat(scenario.folderPath()).containsExactly("Login");
            assertThat(scenario.title()).isEqualTo("Valid credentials");
            assertThat(scenario.steps()).extracting(TestStepRequest::action).containsExactly(
                    "Given a registered user", "When they sign in", "Then the dashboard is shown");
            assertThat(scenario.steps()).extracting(TestStepRequest::expectedResult).containsOnlyNulls();
            assertThat(scenario.location()).isEqualTo("login.feature:2");
        }

        @Test
        void aRuleBecomesAChildFolder() {
            List<Scenario> all = scenarios("""
                    Feature: Login
                      Scenario: Outside
                        Given a
                      Rule: Lockout
                        Scenario: Inside
                          Given b
                    """);

            assertThat(all).extracting(Scenario::folderPath)
                    .containsExactly(List.of("Login"), List.of("Login", "Lockout"));
        }

        @Test
        void aFeatureWithoutANameIsNamedAfterItsFile() {
            Scenario scenario = only("""
                    Feature:
                      Scenario: S
                        Given a
                    """);

            assertThat(scenario.folderPath()).containsExactly("login");
        }

        @Test
        void theScenarioDescriptionLosesItsSourceIndentation() {
            Scenario scenario = only("""
                    Feature: Login
                      Scenario: S
                        Checks the happy path.
                          Indented detail.
                        Given a
                    """);

            assertThat(scenario.description()).isEqualTo("Checks the happy path.\n  Indented detail.");
        }

        @Test
        void anEmptyFileHasNothingToImportAndSaysSo() {
            ParsedFile parsed = GherkinFileParser.parse("empty.feature", "");

            assertThat(parsed.scenarios()).isEmpty();
            assertThat(parsed.warnings()).singleElement().asString().contains("no Feature");
        }

        @Test
        void invalidGherkinIsRefusedWithTheFileAndLine() {
            assertThatThrownBy(() -> scenarios("""
                    Feature: Login
                      Scenario: S
                        Given a
                      @a-tag-with-nothing-after-it
                    """))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageStartingWith("login.feature: (5:");
        }

        @Test
        void aGermanFileIsReadWithItsOwnKeywords() {
            Scenario scenario = only("""
                    # language: de
                    Funktionalität: Anmeldung
                      Szenario: Gültige Daten
                        Angenommen ein Benutzer
                        Wenn er sich anmeldet
                    """);

            assertThat(scenario.folderPath()).containsExactly("Anmeldung");
            assertThat(scenario.steps()).extracting(TestStepRequest::action)
                    .containsExactly("Angenommen ein Benutzer", "Wenn er sich anmeldet");
        }
    }

    @Nested
    class Tags {

        @Test
        void featureRuleAndScenarioTagsBecomeLabels() {
            Scenario scenario = only("""
                    @auth
                    Feature: Login
                      @security
                      Rule: Lockout
                        @smoke @ui
                        Scenario: S
                          Given a
                    """);

            assertThat(scenario.labels()).containsExactlyInAnyOrder("auth", "security", "smoke", "ui");
        }

        @Test
        void theKeyTagIsIdentityNotALabel() {
            Scenario scenario = only("""
                    Feature: Login
                      @tm:PROJ-17 @smoke
                      Scenario: S
                        Given a
                    """);

            assertThat(scenario.key()).isEqualTo("PROJ-17");
            assertThat(scenario.labels()).containsExactly("smoke");
        }

        @Test
        void aPriorityTagSetsThePriority() {
            Scenario scenario = only("""
                    Feature: Login
                      @priority:high
                      Scenario: S
                        Given a
                    """);

            assertThat(scenario.priority()).isEqualTo(Priority.HIGH);
            assertThat(scenario.labels()).isEmpty();
        }

        @Test
        void anUnknownPriorityIsAProblem() {
            Scenario scenario = only("""
                    Feature: Login
                      @priority:urgent
                      Scenario: S
                        Given a
                    """);

            assertThat(scenario.problems()).singleElement().asString().contains("@priority:urgent");
        }

        @Test
        void twoKeysOnOneScenarioAreAProblem() {
            Scenario scenario = only("""
                    Feature: Login
                      @tm:P-1 @tm:P-2
                      Scenario: S
                        Given a
                    """);

            assertThat(scenario.problems()).singleElement().asString().contains("more than one @tm:");
        }

        @Test
        void aKeyOnTheFeatureIsIgnoredWithAWarning() {
            Scenario scenario = only("""
                    @tm:P-1
                    Feature: Login
                      Scenario: S
                        Given a
                    """);

            assertThat(scenario.key()).isNull();
            assertThat(scenario.labels()).isEmpty();
            assertThat(scenario.warnings()).singleElement().asString().contains("@tm:P-1");
        }
    }

    @Nested
    class StepArguments {

        @Test
        void aDocStringBecomesTheStepsTestData() {
            Scenario scenario = only("""
                    Feature: API
                      Scenario: S
                        When I post
                          \"\"\"json
                          {"a": 1}
                          \"\"\"
                    """);

            assertThat(scenario.steps().getFirst().testData()).isEqualTo("{\"a\": 1}");
        }

        @Test
        void aDataTableBecomesPipeRowsInTestData() {
            Scenario scenario = only("""
                    Feature: API
                      Scenario: S
                        Given users
                          | name  | role  |
                          | Alice | a\\|b |
                    """);

            assertThat(scenario.steps().getFirst().testData())
                    .isEqualTo("| name | role |\n| Alice | a\\|b |");
        }

        @Test
        void backgroundStepsBecomePreconditionsFeatureFirstThenRule() {
            List<Scenario> all = scenarios("""
                    Feature: Login
                      Background:
                        Given the app is running
                      Scenario: Plain
                        Given a
                      Rule: Lockout
                        Background:
                          Given a locked account
                        Scenario: Locked
                          Given b
                    """);

            assertThat(all).extracting(Scenario::preconditions).containsExactly(
                    "Given the app is running",
                    "Given the app is running\nGiven a locked account");
        }
    }

    @Nested
    class Outlines {

        @Test
        void exampleRowsBecomeParameterSetsAndPlaceholdersAreRewritten() {
            Scenario scenario = only("""
                    Feature: Currency
                      Scenario Outline: Convert
                        When I convert <amount> to <currency>
                        Then I see <amount> <unknown>

                        Examples:
                          | amount | currency |
                          | 10     | EUR      |
                          | 20     | JPY      |
                    """);

            assertThat(scenario.steps()).extracting(TestStepRequest::action).containsExactly(
                    "When I convert {amount} to {currency}", "Then I see {amount} <unknown>");
            assertThat(scenario.parameterSets()).extracting(SaveParameterSetRequest::name)
                    .containsExactly("Example #1", "Example #2");
            assertThat(scenario.parameterSets().getFirst().values())
                    .isEqualTo(Map.of("amount", "10", "currency", "EUR"));
            assertThat(scenario.parameterSets()).extracting(SaveParameterSetRequest::orderIndex).containsExactly(0, 1);
        }

        @Test
        void namedBlocksNameTheirSetsAndSameNamesKeepCounting() {
            Scenario scenario = only("""
                    Feature: Currency
                      Scenario Outline: Convert
                        When I convert <amount>

                        Examples: Small
                          | amount |
                          | 1      |
                        Examples:
                          | amount |
                          | 2      |
                        Examples:
                          | amount |
                          | 3      |
                    """);

            assertThat(scenario.parameterSets()).extracting(SaveParameterSetRequest::name)
                    .containsExactly("Small #1", "Example #1", "Example #2");
        }

        @Test
        void aHeaderThatIsNotAnIdentifierIsNormalisedWithAWarning() {
            Scenario scenario = only("""
                    Feature: Users
                      Scenario Outline: Greet
                        Then I see "Hello <first name>"

                        Examples:
                          | first name |
                          | Ada        |
                    """);

            assertThat(scenario.steps().getFirst().action()).isEqualTo("Then I see \"Hello {first_name}\"");
            assertThat(scenario.parameterSets().getFirst().values()).isEqualTo(Map.of("first_name", "Ada"));
            assertThat(scenario.warnings()).singleElement().asString().contains("{first_name}");
        }

        @Test
        void anOutlineWithoutExamplesKeepsItsPlaceholdersLiteral() {
            Scenario scenario = only("""
                    Feature: Currency
                      Scenario Outline: Convert
                        When I convert <amount>
                    """);

            assertThat(scenario.parameterSets()).isEmpty();
            assertThat(scenario.steps().getFirst().action()).isEqualTo("When I convert <amount>");
        }

        @Test
        void placeholdersInADocStringAreRewrittenToo() {
            Scenario scenario = only("""
                    Feature: API
                      Scenario Outline: Post
                        When I post
                          \"\"\"
                          {"amount": <amount>}
                          \"\"\"

                        Examples:
                          | amount |
                          | 5      |
                    """);

            assertThat(scenario.steps().getFirst().testData()).isEqualTo("{\"amount\": {amount}}");
        }
    }

    @Nested
    class Preview {

        @Test
        void aScenarioWithoutAFeatureLineIsRead() {
            Scenario scenario = GherkinFileParser.parseScenario("""
                    @tm:P-3
                    Scenario: Valid
                      Given a
                    """);

            assertThat(scenario.key()).isEqualTo("P-3");
            assertThat(scenario.title()).isEqualTo("Valid");
        }

        @Test
        void aLanguageHeaderStaysFirst() {
            Scenario scenario = GherkinFileParser.parseScenario("""
                    # language: de
                    Szenario: Gültig
                      Angenommen ein Benutzer
                    """);

            assertThat(scenario.steps()).extracting(TestStepRequest::action).containsExactly("Angenommen ein Benutzer");
        }

        @Test
        void errorsCountLinesAsTheUserWroteThem() {
            assertThatThrownBy(() -> GherkinFileParser.parseScenario("""
                    Scenario: S
                      Given a
                    @dangling
                    """))
                    .hasMessageStartingWith("scenario: (4:");
        }

        @Test
        void moreThanOneScenarioIsRefused() {
            assertThatThrownBy(() -> GherkinFileParser.parseScenario("""
                    Scenario: One
                      Given a
                    Scenario: Two
                      Given b
                    """))
                    .hasMessage("Expected exactly one scenario, found 2");
        }
    }
}
