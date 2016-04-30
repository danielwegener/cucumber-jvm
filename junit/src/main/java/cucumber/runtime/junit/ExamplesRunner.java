package cucumber.runtime.junit;

import cucumber.runtime.Runtime;
import cucumber.runtime.Stats;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.model.CucumberExamples;
import cucumber.runtime.model.CucumberScenario;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.List;

public class ExamplesRunner extends Suite {
    private final CucumberExamples cucumberExamples;
    private Description description;
    private JUnitReporter jUnitReporter;
    private Stats stats = Stats.IDENTITY;

    protected ExamplesRunner(Runtime runtime, List<Throwable> errors, UndefinedStepsTracker tracker, CucumberExamples cucumberExamples, JUnitReporter jUnitReporter) throws InitializationError {
        super(ExamplesRunner.class, buildRunners(runtime, errors, tracker, cucumberExamples, jUnitReporter));
        this.cucumberExamples = cucumberExamples;
        this.jUnitReporter = jUnitReporter;
    }

    private static List<Runner> buildRunners(Runtime runtime, List<Throwable> errors, UndefinedStepsTracker tracker, CucumberExamples cucumberExamples, JUnitReporter jUnitReporter) {
        List<Runner> runners = new ArrayList<Runner>();
        List<CucumberScenario> exampleScenarios = cucumberExamples.createExampleScenarios();
        for (CucumberScenario scenario : exampleScenarios) {
            try {
                ExecutionUnitRunner exampleScenarioRunner = new ExecutionUnitRunner(runtime, errors, tracker, scenario, jUnitReporter);
                runners.add(exampleScenarioRunner);
            } catch (InitializationError initializationError) {
                initializationError.printStackTrace();
            }
        }
        return runners;
    }

    Stats getStats() {
        return stats;
    }

    @Override
    protected String getName() {
        return cucumberExamples.getExamples().getKeyword() + ": " + cucumberExamples.getExamples().getName();
    }

    @Override
    public Description getDescription() {
        if (description == null) {
            description = Description.createSuiteDescription(getName(), cucumberExamples.getExamples());
            for (Runner child : getChildren()) {
                description.addChild(describeChild(child));
            }
        }
        return description;
    }

    @Override
    public void run(final RunNotifier notifier) {
        jUnitReporter.examples(cucumberExamples.getExamples());
        super.run(notifier);

        for (Runner runner : getChildren()) {
            if (runner instanceof ExecutionUnitRunner) {
                stats = Stats.append(stats, ((ExecutionUnitRunner)runner).getStats());
            }
        }

    }
}
