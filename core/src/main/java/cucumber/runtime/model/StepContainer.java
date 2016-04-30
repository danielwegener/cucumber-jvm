package cucumber.runtime.model;

import cucumber.runtime.Runtime;
import cucumber.runtime.ScenarioImpl;
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

    Runtime.RunStepResult runSteps(ScenarioImpl scenarioResult, UndefinedStepsTracker tracker,  Reporter reporter, Runtime runtime, boolean skip) {
        boolean skipNext = skip;
        RunResult accumulatedRunResult = RunResult.IDENTITY;
        for (Step step : getSteps()) {
            final Runtime.RunStepResult runStepResult = runStep(scenarioResult, tracker, step, reporter, runtime, skipNext);
            accumulatedRunResult = RunResult.append(accumulatedRunResult, runStepResult.runResult);
            if (runStepResult.skipNext) {
                skipNext = true;
            }
        }
        return new Runtime.RunStepResult(skipNext, accumulatedRunResult);
    }

    Runtime.RunStepResult runStep(ScenarioImpl scenarioResult, UndefinedStepsTracker tracker, Step step, Reporter reporter, Runtime runtime, boolean skip) {
        return runtime.runStep(scenarioResult, tracker, cucumberFeature.getPath(), step, reporter, cucumberFeature.getI18n(), skip);
    }
}
