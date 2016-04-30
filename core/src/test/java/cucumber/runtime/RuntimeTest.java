package cucumber.runtime;

import cucumber.api.PendingException;
import cucumber.api.Scenario;
import cucumber.api.StepDefinitionReporter;
import cucumber.runtime.formatter.CucumberJSONFormatter;
import cucumber.runtime.formatter.FormatterSpy;
import cucumber.runtime.io.ClasspathResourceLoader;
import cucumber.runtime.io.Resource;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import gherkin.I18n;
import gherkin.formatter.Formatter;
import gherkin.formatter.JSONFormatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

import static cucumber.runtime.TestHelper.feature;
import static cucumber.runtime.TestHelper.result;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuntimeTest {

    private static final I18n ENGLISH = new I18n("en");

    @Ignore
    @Test
    public void runs_feature_with_json_formatter() throws Exception {
        CucumberFeature feature = feature("test.feature", "" +
                "Feature: feature name\n" +
                "  Background: background name\n" +
                "    Given b\n" +
                "  Scenario: scenario name\n" +
                "    When s\n");
        StringBuilder out = new StringBuilder();
        JSONFormatter jsonFormatter = new CucumberJSONFormatter(out);
        List<Backend> backends = asList(mock(Backend.class));
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        RuntimeOptions runtimeOptions = new RuntimeOptions("");
        Runtime runtime = new Runtime(new ClasspathResourceLoader(classLoader), classLoader,
                runtimeOptions.isDryRun(),
                runtimeOptions.getGlue(), backends);
        Stats stats = new Stats();
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        feature.run(jsonFormatter, jsonFormatter, runtime, stats, errors, tracker);
        jsonFormatter.done();
        String expected = "" +
                "[\n" +
                "  {\n" +
                "    \"id\": \"feature-name\",\n" +
                "    \"description\": \"\",\n" +
                "    \"name\": \"feature name\",\n" +
                "    \"keyword\": \"Feature\",\n" +
                "    \"line\": 1,\n" +
                "    \"elements\": [\n" +
                "      {\n" +
                "        \"description\": \"\",\n" +
                "        \"name\": \"background name\",\n" +
                "        \"keyword\": \"Background\",\n" +
                "        \"line\": 2,\n" +
                "        \"steps\": [\n" +
                "          {\n" +
                "            \"result\": {\n" +
                "              \"status\": \"undefined\"\n" +
                "            },\n" +
                "            \"name\": \"b\",\n" +
                "            \"keyword\": \"Given \",\n" +
                "            \"line\": 3,\n" +
                "            \"match\": {}\n" +
                "          }\n" +
                "        ],\n" +
                "        \"type\": \"background\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"feature-name;scenario-name\",\n" +
                "        \"description\": \"\",\n" +
                "        \"name\": \"scenario name\",\n" +
                "        \"keyword\": \"Scenario\",\n" +
                "        \"line\": 4,\n" +
                "        \"steps\": [\n" +
                "          {\n" +
                "            \"result\": {\n" +
                "              \"status\": \"undefined\"\n" +
                "            },\n" +
                "            \"name\": \"s\",\n" +
                "            \"keyword\": \"When \",\n" +
                "            \"line\": 5,\n" +
                "            \"match\": {}\n" +
                "          }\n" +
                "        ],\n" +
                "        \"type\": \"scenario\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"uri\": \"test.feature\"\n" +
                "  }\n" +
                "]";
        assertEquals(expected, out.toString());
    }

    @Test
    public void strict_without_pending_steps_or_errors() {
        RuntimeOptions runtimeOptions = createStrictRuntimeOptions();

        assertEquals(0x0, Runtime.exitStatus(Collections.<Throwable>emptyList(), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void non_strict_without_pending_steps_or_errors() {
        RuntimeOptions runtimeOptions = createNonStrictRuntimeOptions();

        assertEquals(0x0, Runtime.exitStatus(Collections.<Throwable>emptyList(), new UndefinedStepsTracker(),runtimeOptions.isStrict()));
    }

    @Test
    public void non_strict_with_undefined_steps() {
        RuntimeOptions runtimeOptions = createNonStrictRuntimeOptions();

        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        tracker.addUndefinedStep(new Step(null, "Given ", "A", 1, null, null), ENGLISH);
        assertEquals(0x0, Runtime.exitStatus(Collections.<Throwable>emptyList(), tracker, runtimeOptions.isStrict()));
    }

    @Test
    public void strict_with_undefined_steps() {
        RuntimeOptions runtimeOptions = createStrictRuntimeOptions();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        tracker.addUndefinedStep(new Step(null, "Given ", "A", 1, null, null), ENGLISH);
        assertEquals(0x1, Runtime.exitStatus(Collections.<Throwable>emptyList(), tracker, runtimeOptions.isStrict()));
    }

    @Test
    public void strict_with_pending_steps_and_no_errors() {
        RuntimeOptions runtimeOptions = createStrictRuntimeOptions();

        assertEquals(0x1, Runtime.exitStatus(Arrays.<Throwable>asList(new PendingException()), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void non_strict_with_pending_steps() {
        RuntimeOptions runtimeOptions = createNonStrictRuntimeOptions();
        assertEquals(0x0, Runtime.exitStatus(Arrays.<Throwable>asList(new PendingException()), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void non_strict_with_failed_junit_assumption_prior_to_junit_412() {
        RuntimeOptions runtimeOptions = createNonStrictRuntimeOptions();

        assertEquals(0x0, Runtime.exitStatus(Arrays.<Throwable>asList(
                new AssumptionViolatedException("should be treated like pending")), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void non_strict_with_failed_junit_assumption_from_junit_412_on() {
        RuntimeOptions runtimeOptions = createNonStrictRuntimeOptions();

        assertEquals(0x0, Runtime.exitStatus(Arrays.<Throwable>asList(
                new org.junit.AssumptionViolatedException("should be treated like pending")), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void non_strict_with_errors() {
        RuntimeOptions runtimeOptions = createNonStrictRuntimeOptions();

        assertEquals(0x1, Runtime.exitStatus(Arrays.<Throwable>asList(new RuntimeException()), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void strict_with_errors() {
        RuntimeOptions runtimeOptions = createStrictRuntimeOptions();

        assertEquals(0x1, Runtime.exitStatus(Arrays.<Throwable>asList(new RuntimeException()), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void should_pass_if_no_features_are_found() throws IOException {
        ResourceLoader resourceLoader = createResourceLoaderThatFindsNoFeatures();
        RuntimeOptions runtimeOptions = createStrictRuntimeOptions();
        Runtime runtime = createRuntime(runtimeOptions, resourceLoader);

        runtime.run(runtimeOptions);

        assertEquals(0x0, Runtime.exitStatus(Collections.<Throwable>emptyList(), new UndefinedStepsTracker(), runtimeOptions.isStrict()));
    }

    @Test
    public void reports_step_definitions_to_plugin() throws IOException, NoSuchMethodException {
        RuntimeOptions runtimeOptions = createRuntimeOptions("--plugin", "cucumber.runtime.RuntimeTest$StepdefsPrinter");
        Runtime runtime = createRuntime(runtimeOptions);

        StubStepDefinition stepDefinition = new StubStepDefinition(this, getClass().getMethod("reports_step_definitions_to_plugin"), "some pattern");
        runtime.getGlue().addStepDefinition(stepDefinition);
        runtime.run(runtimeOptions);

        assertSame(stepDefinition, StepdefsPrinter.instance.stepDefinition);
    }

    public static class StepdefsPrinter implements StepDefinitionReporter {
        public static StepdefsPrinter instance;
        public StepDefinition stepDefinition;

        public StepdefsPrinter() {
            instance = this;
        }

        @Override
        public void stepDefinition(StepDefinition stepDefinition) {
            this.stepDefinition = stepDefinition;
        }
    }


    @Test
    public void should_throw_cucumer_exception_if_no_backends_are_found() throws Exception {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            new Runtime(new ClasspathResourceLoader(classLoader), classLoader,
                    false, Collections.<String>emptyList(),
                    Collections.<Backend>emptyList());
            fail("A CucumberException should have been thrown");
        } catch (CucumberException e) {
            assertEquals("No backends were found. Please make sure you have a backend module on your CLASSPATH.", e.getMessage());
        }
    }

    @Test
    public void should_add_passed_result_to_the_summary_counter() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);
        StepDefinitionMatch match = mock(StepDefinitionMatch.class);

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(match, runtimeOptions);
        Stats stats = new Stats();
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, new Stats.StatsFormatOptions(true), new PrintStream(baos), false);

        assertThat(baos.toString(), startsWith(String.format(
                "1 Scenarios (1 passed)%n" +
                        "1 Steps (1 passed)%n")));
    }

    @Test
    public void should_add_pending_result_to_the_summary_counter() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);
        StepDefinitionMatch match = createExceptionThrowingMatch(new PendingException());

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(match, runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format("" +
                "1 Scenarios (1 pending)%n" +
                "1 Steps (1 pending)%n")));
    }

    @Test
    public void should_add_failed_result_to_the_summary_counter() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);
        StepDefinitionMatch match = createExceptionThrowingMatch(new Exception());

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(match, runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format("" +
                "1 Scenarios (1 failed)%n" +
                "1 Steps (1 failed)%n")));
    }

    @Test
    public void should_add_ambiguous_match_as_failed_result_to_the_summary_counter() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlueWithAmbiguousMatch(runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format(""+
                "1 Scenarios (1 failed)%n" +
                "1 Steps (1 failed)%n")));
    }

    @Test
    public void should_add_skipped_result_to_the_summary_counter() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);
        StepDefinitionMatch match = createExceptionThrowingMatch(new Exception());

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(match, runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(2));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format("" +
                "1 Scenarios (1 failed)%n" +
                "2 Steps (1 failed, 1 skipped)%n")));
    }

    @Test
    public void should_add_undefined_result_to_the_summary_counter() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(null, runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format("" +
                "1 Scenarios (1 undefined)%n" +
                "1 Steps (1 undefined)%n")));
    }

    @Test
    public void should_fail_the_scenario_if_before_fails() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);
        StepDefinitionMatch match = mock(StepDefinitionMatch.class);
        HookDefinition hook = createExceptionThrowingHook();

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(match, hook, true, runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format("" +
                "1 Scenarios (1 failed)%n" +
                "1 Steps (1 skipped)%n")));
    }

    @Test
    public void should_fail_the_scenario_if_after_fails() throws Throwable {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Reporter reporter = mock(Reporter.class);
        StepDefinitionMatch match = mock(StepDefinitionMatch.class);
        HookDefinition hook = createExceptionThrowingHook();

        RuntimeOptions runtimeOptions = createRuntimeOptions("--monochrome");
        Runtime runtime = createRuntimeWithMockedGlue(match, hook, false, runtimeOptions);
        Stats stats = new Stats();
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        runScenario(reporter, runtime, stats, errors, tracker, stepCount(1));
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, new PrintStream(baos), false);

        assertThat(baos.toString(), containsString(String.format("" +
                "1 Scenarios (1 failed)%n" +
                "1 Steps (1 passed)%n")));
    }

    @Test
    public void should_make_scenario_name_available_to_hooks() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        HookDefinition beforeHook = mock(HookDefinition.class);
        when(beforeHook.matches(anyCollectionOf(Tag.class))).thenReturn(true);

        Runtime runtime = createRuntimeWithMockedGlue(mock(StepDefinitionMatch.class), beforeHook, true, createRuntimeOptions());
        Stats stats = new Stats();
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        feature.run(mock(Formatter.class), mock(Reporter.class), runtime, stats, errors, tracker);

        ArgumentCaptor<Scenario> capturedScenario = ArgumentCaptor.forClass(Scenario.class);
        verify(beforeHook).execute(capturedScenario.capture());
        assertEquals("scenario name", capturedScenario.getValue().getName());
    }

    @Test
    public void should_make_scenario_id_available_to_hooks() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        HookDefinition beforeHook = mock(HookDefinition.class);
        when(beforeHook.matches(anyCollectionOf(Tag.class))).thenReturn(true);

        Runtime runtime = createRuntimeWithMockedGlue(mock(StepDefinitionMatch.class), beforeHook, true, createRuntimeOptions());
        Stats stats = new Stats();
        List<Throwable> throwables = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        feature.run(mock(Formatter.class), mock(Reporter.class), runtime, stats, throwables, tracker);

        ArgumentCaptor<Scenario> capturedScenario = ArgumentCaptor.forClass(Scenario.class);
        verify(beforeHook).execute(capturedScenario.capture());
        assertEquals("feature-name;scenario-name", capturedScenario.getValue().getId());
    }

    @Test
    public void should_call_formatter_for_two_scenarios_with_background() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature", "" +
                "Feature: feature name\n" +
                "  Background: background\n" +
                "    Given first step\n" +
                "  Scenario: scenario_1 name\n" +
                "    When second step\n" +
                "    Then third step\n" +
                "  Scenario: scenario_2 name\n" +
                "    Then second step\n");
        Map<String, Result> stepsToResult = new HashMap<String, Result>();
        stepsToResult.put("first step", result("passed"));
        stepsToResult.put("second step", result("passed"));
        stepsToResult.put("third step", result("passed"));

        String formatterOutput = runFeatureWithFormatterSpy(feature, stepsToResult);

        assertEquals("" +
                "uri\n" +
                "feature\n" +
                "  startOfScenarioLifeCycle\n" +
                "  background\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "  scenario\n" +
                "    step\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "    match\n" +
                "    result\n" +
                "  endOfScenarioLifeCycle\n" +
                "  startOfScenarioLifeCycle\n" +
                "  background\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "  scenario\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "  endOfScenarioLifeCycle\n" +
                "eof\n" +
                "done\n" +
                "close\n", formatterOutput);
    }

    @Test
    public void should_call_formatter_for_scenario_outline_with_two_examples_table_and_background() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature", "" +
                "Feature: feature name\n" +
                "  Background: background\n" +
                "    Given first step\n" +
                "  Scenario Outline: scenario outline name\n" +
                "    When <x> step\n" +
                "    Then <y> step\n" +
                "    Examples: examples 1 name\n" +
                "      |   x    |   y   |\n" +
                "      | second | third |\n" +
                "      | second | third |\n" +
                "    Examples: examples 2 name\n" +
                "      |   x    |   y   |\n" +
                "      | second | third |\n");
        Map<String, Result> stepsToResult = new HashMap<String, Result>();
        stepsToResult.put("first step", result("passed"));
        stepsToResult.put("second step", result("passed"));
        stepsToResult.put("third step", result("passed"));

        String formatterOutput = runFeatureWithFormatterSpy(feature, stepsToResult);

        assertEquals("" +
                "uri\n" +
                "feature\n" +
                "  scenarioOutline\n" +
                "    step\n" +
                "    step\n" +
                "  examples\n" +
                "  startOfScenarioLifeCycle\n" +
                "  background\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "  scenario\n" +
                "    step\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "    match\n" +
                "    result\n" +
                "  endOfScenarioLifeCycle\n" +
                "  startOfScenarioLifeCycle\n" +
                "  background\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "  scenario\n" +
                "    step\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "    match\n" +
                "    result\n" +
                "  endOfScenarioLifeCycle\n" +
                "  examples\n" +
                "  startOfScenarioLifeCycle\n" +
                "  background\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "  scenario\n" +
                "    step\n" +
                "    step\n" +
                "    match\n" +
                "    result\n" +
                "    match\n" +
                "    result\n" +
                "  endOfScenarioLifeCycle\n" +
                "eof\n" +
                "done\n" +
                "close\n", formatterOutput);
    }

    private String runFeatureWithFormatterSpy(CucumberFeature feature, Map<String, Result> stepsToResult) throws Throwable {
        FormatterSpy formatterSpy = new FormatterSpy();
        TestHelper.runFeatureWithFormatter(feature, stepsToResult, Collections.<SimpleEntry<String, Result>>emptyList(), 0L, formatterSpy, formatterSpy);
        return formatterSpy.toString();
    }

    private StepDefinitionMatch createExceptionThrowingMatch(Exception exception) throws Throwable {
        StepDefinitionMatch match = mock(StepDefinitionMatch.class);
        doThrow(exception).when(match).runStep((I18n) any());
        return match;
    }

    private HookDefinition createExceptionThrowingHook() throws Throwable {
        HookDefinition hook = mock(HookDefinition.class);
        when(hook.matches(anyCollectionOf(Tag.class))).thenReturn(true);
        doThrow(new Exception()).when(hook).execute((Scenario) any());
        return hook;
    }

    public boolean runStep(ScenarioImpl scenarioResult, Reporter reporter, Runtime runtime, Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker,  boolean skip) {
        Step step = mock(Step.class);
        I18n i18n = mock(I18n.class);
        return runtime.runStep(scenarioResult, stats, errors, tracker, "<featurePath>", step, reporter, i18n, skip);
    }

    private ResourceLoader createResourceLoaderThatFindsNoFeatures() {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        when(resourceLoader.resources(anyString(), eq(".feature"))).thenReturn(Collections.<Resource>emptyList());
        return resourceLoader;
    }

    private Runtime createRuntime(RuntimeOptions runtimeOptions, ResourceLoader resourceLoader) {
        return createRuntime(resourceLoader, Thread.currentThread().getContextClassLoader(), runtimeOptions);
    }

    private RuntimeOptions createStrictRuntimeOptions() {
        return createRuntimeOptions("-g", "anything", "--strict");
    }

    private RuntimeOptions createNonStrictRuntimeOptions() {
        return createRuntimeOptions("-g", "anything");
    }

    private RuntimeOptions createRuntimeOptions(String... runtimeArgs) {
        return new RuntimeOptions(asList(runtimeArgs));
    }

    private Runtime createRuntime(RuntimeOptions runtimeOptions) {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return createRuntime(resourceLoader, classLoader, runtimeOptions);
    }

    private Runtime createRuntime(ResourceLoader resourceLoader, ClassLoader classLoader, RuntimeOptions runtimeOptions) {
        Backend backend = mock(Backend.class);
        Collection<Backend> backends = Arrays.asList(backend);

        return new Runtime(resourceLoader, classLoader, runtimeOptions.isDryRun(),
                runtimeOptions.getGlue(), backends);
    }

    private Runtime createRuntimeWithMockedGlue(StepDefinitionMatch match, RuntimeOptions runtimeOptions) {
        return createRuntimeWithMockedGlue(match, false, mock(HookDefinition.class), false, runtimeOptions);
    }

    private Runtime createRuntimeWithMockedGlue(StepDefinitionMatch match, HookDefinition hook, boolean isBefore,
                                                RuntimeOptions runtimeOptions) {
        return createRuntimeWithMockedGlue(match, false, hook, isBefore, runtimeOptions);
    }

    private Runtime createRuntimeWithMockedGlueWithAmbiguousMatch(RuntimeOptions runtimeOptions) {
        return createRuntimeWithMockedGlue(mock(StepDefinitionMatch.class), true, mock(HookDefinition.class), false, runtimeOptions);
    }

    private Runtime createRuntimeWithMockedGlue(StepDefinitionMatch match, boolean isAmbiguous, HookDefinition hook,
                                                boolean isBefore, RuntimeOptions runtimeOptions) {
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        ClassLoader classLoader = mock(ClassLoader.class);
        Backend backend = mock(Backend.class);
        RuntimeGlue glue = mock(RuntimeGlue.class);
        mockMatch(glue, match, isAmbiguous);
        mockHook(glue, hook, isBefore);
        Collection<Backend> backends = Arrays.asList(backend);

        return new Runtime(resourceLoader, classLoader, runtimeOptions.isDryRun(),
                runtimeOptions.getGlue(), backends, glue);
    }

    private void mockMatch(RuntimeGlue glue, StepDefinitionMatch match, boolean isAmbiguous) {
        if (isAmbiguous) {
            Exception exception = new AmbiguousStepDefinitionsException(Arrays.asList(match, match));
            doThrow(exception).when(glue).stepDefinitionMatch(anyString(), (Step) any(), (I18n) any(), any(UndefinedStepsTracker.class));
        } else {
            when(glue.stepDefinitionMatch(anyString(), (Step) any(), (I18n) any(), any(UndefinedStepsTracker.class))).thenReturn(match);
        }
    }

    private void mockHook(RuntimeGlue glue, HookDefinition hook, boolean isBefore) {
        if (isBefore) {
            when(glue.getBeforeHooks()).thenReturn(Arrays.asList(hook));
        } else {
            when(glue.getAfterHooks()).thenReturn(Arrays.asList(hook));
        }
    }

    private void runScenario(Reporter reporter, Runtime runtime, Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker, int stepCount) {
        gherkin.formatter.model.Scenario gherkinScenario = mock(gherkin.formatter.model.Scenario.class);
        final ScenarioImpl scenarioResult = runtime.buildBackendWorlds(reporter, Collections.<Tag>emptySet(), gherkinScenario);
        boolean skipNext = runtime.runBeforeHooks(scenarioResult, stats, errors, reporter, Collections.<Tag>emptySet());
        for (int i = 0; i < stepCount; ++i) {
            skipNext = runStep(scenarioResult, reporter, runtime, stats, errors, tracker, skipNext);
        }
        runtime.runAfterHooks(scenarioResult, stats, errors, reporter, Collections.<Tag>emptySet());
        runtime.disposeBackendWorlds();
        stats.addScenario(scenarioResult.getStatus(), "scenario designation");
    }

    private int stepCount(int stepCount) {
        return stepCount;
    }
}
