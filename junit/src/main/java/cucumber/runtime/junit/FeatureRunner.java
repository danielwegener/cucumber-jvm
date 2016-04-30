package cucumber.runtime.junit;

import cucumber.runtime.CucumberException;
import cucumber.runtime.Runtime;
import cucumber.runtime.Stats;
import cucumber.runtime.UndefinedStepsTracker;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.CucumberScenario;
import cucumber.runtime.model.CucumberScenarioOutline;
import cucumber.runtime.model.CucumberTagStatement;
import gherkin.formatter.model.Feature;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import java.util.ArrayList;
import java.util.List;

public class FeatureRunner extends ParentRunner<ParentRunner> {
    private final List<ParentRunner> children = new ArrayList<ParentRunner>();

    private final CucumberFeature cucumberFeature;
    private final Runtime runtime;
    private Stats stats = Stats.IDENTITY;
    private final List<Throwable> errors;
    private final UndefinedStepsTracker tracker;
    private final JUnitReporter jUnitReporter;
    private Description description;

    public FeatureRunner(CucumberFeature cucumberFeature, Runtime runtime, List<Throwable> errors, UndefinedStepsTracker tracker, JUnitReporter jUnitReporter) throws InitializationError {
        super(null);
        this.cucumberFeature = cucumberFeature;
        this.runtime = runtime;
        this.errors = errors;
        this.tracker = tracker;
        this.jUnitReporter = jUnitReporter;
        buildFeatureElementRunners();
    }

    public Stats getStats() {
        return stats;
    }

    @Override
    public String getName() {
        Feature feature = cucumberFeature.getGherkinFeature();
        return feature.getKeyword() + ": " + feature.getName();
    }

    @Override
    public Description getDescription() {
        if (description == null) {
            description = Description.createSuiteDescription(getName(), cucumberFeature.getGherkinFeature());
            for (ParentRunner child : getChildren()) {
                description.addChild(describeChild(child));
            }
        }
        return description;
    }

    @Override
    protected List<ParentRunner> getChildren() {
        return children;
    }

    @Override
    protected Description describeChild(ParentRunner child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(ParentRunner child, RunNotifier notifier) {
        child.run(notifier);
    }

    @Override
    public void run(RunNotifier notifier) {
        jUnitReporter.uri(cucumberFeature.getPath());
        jUnitReporter.feature(cucumberFeature.getGherkinFeature());
        super.run(notifier);
        for (ParentRunner child : getChildren()) {
            if (child instanceof ExecutionUnitRunner) {
                stats = Stats.append(stats, ((ExecutionUnitRunner)child).getStats());
            } else if (child instanceof ScenarioOutlineRunner) {
                stats = Stats.append(stats, ((ScenarioOutlineRunner)child).getStats());
            }
        }

        jUnitReporter.eof();
    }

    private void buildFeatureElementRunners() {
        for (CucumberTagStatement cucumberTagStatement : cucumberFeature.getFeatureElements()) {
            try {
                ParentRunner featureElementRunner;
                if (cucumberTagStatement instanceof CucumberScenario) {
                    featureElementRunner = new ExecutionUnitRunner(runtime, errors, tracker, (CucumberScenario) cucumberTagStatement, jUnitReporter);
                } else {
                    featureElementRunner = new ScenarioOutlineRunner(runtime, errors, tracker, (CucumberScenarioOutline) cucumberTagStatement, jUnitReporter);
                }
                children.add(featureElementRunner);
            } catch (InitializationError e) {
                throw new CucumberException("Failed to create scenario runner", e);
            }
        }
    }

}
