package cucumber.api;

import cucumber.runtime.Stats;

public interface SummaryPrinter {
    void print(cucumber.runtime.Runtime runtime, Stats stats);
}
