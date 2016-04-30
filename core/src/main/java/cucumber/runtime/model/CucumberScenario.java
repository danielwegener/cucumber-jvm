package cucumber.runtime.model;

import cucumber.runtime.Runtime;
import cucumber.runtime.ScenarioImpl;
import cucumber.runtime.Stats;
import cucumber.runtime.UndefinedStepsTracker;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Row;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.Tag;

import java.util.Collections;
import java.util.Set;

public class CucumberScenario extends CucumberTagStatement {
    private final CucumberBackground cucumberBackground;
    private final Scenario scenario;

    public CucumberScenario(CucumberFeature cucumberFeature, CucumberBackground cucumberBackground, Scenario scenario) {
        super(cucumberFeature, scenario);
        this.cucumberBackground = cucumberBackground;
        this.scenario = scenario;
    }

    public CucumberScenario(CucumberFeature cucumberFeature, CucumberBackground cucumberBackground, Scenario exampleScenario, Row example) {
        super(cucumberFeature, exampleScenario, example);
        this.cucumberBackground = cucumberBackground;
        this.scenario = exampleScenario;
    }

    public CucumberBackground getCucumberBackground() {
        return cucumberBackground;
    }

    /**
     * This method is called when Cucumber is run from the CLI or JUnit
     */
    @Override
    public RunResult run(Formatter formatter, Reporter reporter, Runtime runtime, UndefinedStepsTracker tracker) {
        Set<Tag> tags = tagsAndInheritedTags();
        final ScenarioImpl scenarioResult = runtime.buildBackendWorlds(reporter, tags, this.scenario);
        tracker.reset();
        formatter.startOfScenarioLifeCycle((Scenario) getGherkinModel());

        RunResult aggregatedRunResult = new RunResult(new Stats(), Collections.<Throwable>emptyList());

        final Runtime.RunStepResult beforeHookResult = runtime.runBeforeHooks(scenarioResult, reporter, tags);
        aggregatedRunResult = RunResult.append(aggregatedRunResult, beforeHookResult.runResult);
        boolean skipNext = beforeHookResult.skipNext;

        final Runtime.RunStepResult backgroundResult = runBackground(scenarioResult, formatter, reporter, runtime, tracker, skipNext);
        skipNext = backgroundResult.skipNext;
        aggregatedRunResult = RunResult.append(aggregatedRunResult, backgroundResult.runResult);

        format(formatter);
        final Runtime.RunStepResult runStepResult = runSteps(scenarioResult, tracker, reporter, runtime, skipNext);
        aggregatedRunResult = RunResult.append(aggregatedRunResult, runStepResult.runResult);

        final Runtime.RunStepResult runAfterHooksResult = runtime.runAfterHooks(scenarioResult, reporter, tags);
        aggregatedRunResult = RunResult.append(aggregatedRunResult, runAfterHooksResult.runResult);

        formatter.endOfScenarioLifeCycle((Scenario) getGherkinModel());
        runtime.disposeBackendWorlds();

        // TODO this mutates the state
        aggregatedRunResult.stats.addScenario(scenarioResult.getStatus(), createScenarioDesignation());

        return aggregatedRunResult;
    }

    private String createScenarioDesignation() {
        return cucumberFeature.getPath() + ":" + Integer.toString(scenario.getLine()) + " # " +
                scenario.getKeyword() + ": " + scenario.getName();
    }

    private Runtime.RunStepResult runBackground(ScenarioImpl scenarioResult, Formatter formatter, Reporter reporter, Runtime runtime, UndefinedStepsTracker tracker, boolean skipNext) {
        if (cucumberBackground != null) {
            cucumberBackground.format(formatter);
            return cucumberBackground.runSteps(scenarioResult, tracker, reporter, runtime, skipNext);
        }
        return new Runtime.RunStepResult(skipNext, RunResult.IDENTITY);
    }
}
