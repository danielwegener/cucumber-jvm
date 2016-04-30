package cucumber.runtime.model;

import cucumber.runtime.Runtime;
import cucumber.runtime.ScenarioImpl;
import cucumber.runtime.Stats;
import cucumber.runtime.UndefinedStepsTracker;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.BasicStatement;
import gherkin.formatter.model.Step;

import java.util.ArrayList;
import java.util.List;

public class StepContainer {
    private final List<Step> steps = new ArrayList<Step>();
    final CucumberFeature cucumberFeature;
    private final BasicStatement statement;

    StepContainer(CucumberFeature cucumberFeature, BasicStatement statement) {
        this.cucumberFeature = cucumberFeature;
        this.statement = statement;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public void step(Step step) {
        steps.add(step);
    }

    void format(Formatter formatter) {
        statement.replay(formatter);
        for (Step step : getSteps()) {
            formatter.step(step);
        }
    }

    boolean runSteps(ScenarioImpl scenarioResult, Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker,  Reporter reporter, Runtime runtime, boolean skip) {
        boolean skipNext = skip;
        for (Step step : getSteps()) {
            if (runStep(scenarioResult, stats, errors, tracker, step, reporter, runtime, skipNext)) {
                skipNext = true;
            }
        }
        return skipNext;
    }

    boolean runStep(ScenarioImpl scenarioResult, Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker, Step step, Reporter reporter, Runtime runtime, boolean skip) {
        return runtime.runStep(scenarioResult, stats, errors, tracker, cucumberFeature.getPath(), step, reporter, cucumberFeature.getI18n(), skip);
    }
}
