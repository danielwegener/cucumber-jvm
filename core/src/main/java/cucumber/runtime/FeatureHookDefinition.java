package cucumber.runtime;

import cucumber.api.Feature;
import cucumber.api.Scenario;
import gherkin.formatter.model.Tag;

import java.util.Collection;

public interface FeatureHookDefinition extends HasOrder {
    /**
     * The source line where the step definition is defined.
     * Example: foo/bar/Zap.brainfuck:42
     *
     * @param detail true if extra detailed location information should be included.
     */
    String getLocation(boolean detail);

    void execute(Feature feature) throws Throwable;

    boolean matches(Collection<Tag> tags);

}
