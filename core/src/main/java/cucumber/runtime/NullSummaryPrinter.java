package cucumber.runtime;

import cucumber.api.SummaryPrinter;

import java.util.List;

public class NullSummaryPrinter implements SummaryPrinter {

    @Override
    public void print(Runtime runtime, Stats stats, List<Throwable> errors, UndefinedStepsTracker undefinedStepsTracker) {
        // Do nothing
    }

}
