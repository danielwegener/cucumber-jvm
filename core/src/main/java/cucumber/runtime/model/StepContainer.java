package cucumber.runtime.model;

import cucumber.runtime.Runtime;
import cucumber.runtime.Utils;
import gherkin.formatter.Formatter;
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

    ExecutionResult runSteps(Runtime runtime) {
        return runNextStep(runtime, getSteps());
    }

    private ExecutionResult runNextStep(Runtime runtime, List<Step> remainingSteps) {
        if (remainingSteps.isEmpty()) return ExecutionResult.EMPTY.done();
        return runStep(remainingSteps.get(0), runtime)
                .withContinuation(() -> runNextStep(runtime, Utils.tail(remainingSteps)));
    }


    private ExecutionResult runStep(Step step, Runtime runtime) {
        return runtime.runStep(cucumberFeature.getPath(), step, cucumberFeature.getI18n());
    }
}
