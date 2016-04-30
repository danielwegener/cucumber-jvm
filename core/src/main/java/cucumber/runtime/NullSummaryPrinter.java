package cucumber.runtime;

import cucumber.api.SummaryPrinter;

import java.util.List;

public class NullSummaryPrinter implements SummaryPrinter {

    @Override
    public void print(Stats.StatsFormatOptions statsFormatOptions, Stats stats, List<Throwable> errors, List<String> snippets, boolean isStrict) {
        // Do nothing
    }

}
