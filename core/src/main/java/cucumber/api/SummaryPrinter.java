package cucumber.api;

import cucumber.runtime.Stats;
import cucumber.runtime.UndefinedStepsTracker;

import java.util.List;

public interface SummaryPrinter {
    void print(cucumber.runtime.Runtime runtime, Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker);
}
