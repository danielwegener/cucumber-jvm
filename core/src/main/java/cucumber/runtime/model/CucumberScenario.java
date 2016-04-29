package cucumber.runtime.model;

import cucumber.runtime.Runtime;
import cucumber.runtime.ScenarioImpl;
import cucumber.runtime.Stats;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Row;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.Tag;

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
    public void run(Formatter formatter, Reporter reporter, Runtime runtime, Stats stats) {
        Set<Tag> tags = tagsAndInheritedTags();
        final ScenarioImpl scenarioResult = runtime.buildBackendWorlds(reporter, tags, this.scenario);
        formatter.startOfScenarioLifeCycle((Scenario) getGherkinModel());

        boolean skipNext = runtime.runBeforeHooks(scenarioResult, stats, reporter, tags);


        skipNext = runBackground(scenarioResult, formatter, reporter, runtime, stats, skipNext);
        format(formatter);
        runSteps(scenarioResult, stats, reporter, runtime, skipNext);

        runtime.runAfterHooks(scenarioResult, stats, reporter, tags);
        formatter.endOfScenarioLifeCycle((Scenario) getGherkinModel());
        runtime.disposeBackendWorlds();
        stats.addScenario(scenarioResult.getStatus(), createScenarioDesignation());
    }

    private String createScenarioDesignation() {
        return cucumberFeature.getPath() + ":" + Integer.toString(scenario.getLine()) + " # " +
                scenario.getKeyword() + ": " + scenario.getName();
    }

    private boolean runBackground(ScenarioImpl scenarioResult, Formatter formatter, Reporter reporter, Runtime runtime, Stats stats, boolean skipNext) {
        if (cucumberBackground != null) {
            cucumberBackground.format(formatter);
            return cucumberBackground.runSteps(scenarioResult, stats, reporter, runtime, skipNext);
        }
        return skipNext;
    }
}
