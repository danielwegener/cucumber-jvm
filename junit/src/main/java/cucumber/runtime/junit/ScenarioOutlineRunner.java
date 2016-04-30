package cucumber.runtime.junit;

import cucumber.runtime.Runtime;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.model.CucumberExamples;
import cucumber.runtime.model.CucumberScenarioOutline;
import cucumber.runtime.model.RunResult;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.List;

public class ScenarioOutlineRunner extends Suite {
    private final CucumberScenarioOutline cucumberScenarioOutline;
    private final JUnitReporter jUnitReporter;
    private Description description;
    private RunResult runResult = RunResult.IDENTITY;

    public ScenarioOutlineRunner(Runtime runtime, UndefinedStepsTracker tracker, CucumberScenarioOutline cucumberScenarioOutline, JUnitReporter jUnitReporter) throws InitializationError {
        super(null, buildRunners(runtime, tracker, cucumberScenarioOutline, jUnitReporter));
        this.cucumberScenarioOutline = cucumberScenarioOutline;
        this.jUnitReporter = jUnitReporter;
    }

    private static List<Runner> buildRunners(Runtime runtime, UndefinedStepsTracker tracker, CucumberScenarioOutline cucumberScenarioOutline, JUnitReporter jUnitReporter) throws InitializationError {
        List<Runner> runners = new ArrayList<Runner>();
        for (CucumberExamples cucumberExamples : cucumberScenarioOutline.getCucumberExamplesList()) {
            runners.add(new ExamplesRunner(runtime, tracker, cucumberExamples, jUnitReporter));
        }
        return runners;
    }

    public RunResult getRunResult() {
        return runResult;
    }

    @Override
    public String getName() {
        return cucumberScenarioOutline.getVisualName();
    }

    @Override
    public Description getDescription() {
        if (description == null) {
            description = Description.createSuiteDescription(getName(), cucumberScenarioOutline.getGherkinModel());
            for (Runner child : getChildren()) {
                description.addChild(describeChild(child));
            }
        }
        return description;
    }

    @Override
    public void run(final RunNotifier notifier) {
        cucumberScenarioOutline.formatOutlineScenario(jUnitReporter);
        super.run(notifier);
        for (Runner runner : getChildren()) {
            if (runner instanceof ExamplesRunner) {
                runResult = RunResult.append(runResult, ((ExamplesRunner)runner).getStats());
            }
        }

    }
}
