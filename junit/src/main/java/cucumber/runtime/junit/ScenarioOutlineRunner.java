package cucumber.runtime.junit;

import cucumber.runtime.Runtime;
import cucumber.runtime.Stats;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.model.CucumberExamples;
import cucumber.runtime.model.CucumberScenarioOutline;
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
    private Stats stats = Stats.IDENTITY;

    public ScenarioOutlineRunner(Runtime runtime, List<Throwable> errors, UndefinedStepsTracker tracker, CucumberScenarioOutline cucumberScenarioOutline, JUnitReporter jUnitReporter) throws InitializationError {
        super(null, buildRunners(runtime, errors, tracker, cucumberScenarioOutline, jUnitReporter));
        this.cucumberScenarioOutline = cucumberScenarioOutline;
        this.jUnitReporter = jUnitReporter;
    }

    private static List<Runner> buildRunners(Runtime runtime, List<Throwable> errors, UndefinedStepsTracker tracker, CucumberScenarioOutline cucumberScenarioOutline, JUnitReporter jUnitReporter) throws InitializationError {
        List<Runner> runners = new ArrayList<Runner>();
        for (CucumberExamples cucumberExamples : cucumberScenarioOutline.getCucumberExamplesList()) {
            runners.add(new ExamplesRunner(runtime, errors, tracker, cucumberExamples, jUnitReporter));
        }
        return runners;
    }

    public Stats getStats() {
        return stats;
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
                stats = stats.append(stats, ((ExamplesRunner)runner).getStats());
            }
        }

    }
}
