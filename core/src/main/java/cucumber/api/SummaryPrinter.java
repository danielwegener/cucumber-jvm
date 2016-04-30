package cucumber.api;

import cucumber.runtime.Stats;

import java.util.List;

public interface SummaryPrinter {
    void print(Stats.StatsFormatOptions statsFormatOptions, Stats stats, List<Throwable> errors, List<String> snippets, boolean isStrict);
}
