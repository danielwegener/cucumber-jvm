package cucumber.runtime;

import cucumber.api.Feature;
import cucumber.api.Scenario;
import cucumber.api.Step;
import gherkin.formatter.model.Tag;

import java.util.Collection;

public interface StepHookDefinition extends HasOrder {
    /**
     * The source line where the step definition is defined.
     * Example: foo/bar/Zap.brainfuck:42
     *
     * @param detail true if extra detailed location information should be included.
     */
    String getLocation(boolean detail);

    void execute(Feature feature, Scenario scenario, Step step) throws Throwable;

    boolean matches(Collection<Tag> tags);

}
