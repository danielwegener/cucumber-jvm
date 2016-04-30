package cucumber.runtime.model;

import cucumber.runtime.Stats;
import cucumber.runtime.Utils;

import java.util.Collections;
import java.util.List;

public class RunResult {
    public final Stats stats;
    public final List<Throwable> errors;

    public static RunResult IDENTITY = new RunResult(Stats.IDENTITY, Collections.<Throwable>emptyList());

    public static RunResult append(RunResult a, RunResult b) {
        return new RunResult(Stats.append(a.stats, b.stats), Utils.append(a.errors, b.errors));
    }

    public RunResult(Stats stats, List<Throwable> errors) {
        this.stats = stats;
        this.errors = errors;
    }
}
