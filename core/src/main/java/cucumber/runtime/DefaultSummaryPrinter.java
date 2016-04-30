package cucumber.runtime;

import cucumber.api.SummaryPrinter;

import java.io.PrintStream;
import java.util.List;

public class DefaultSummaryPrinter implements SummaryPrinter {
    private final PrintStream out;

    public DefaultSummaryPrinter() {
        this.out = System.out;
    }

    @Override
    public void print(Stats.StatsFormatOptions statsFormatOptions, Stats stats,  List<Throwable> errors, List<String> snippets, boolean isStrict) {
        out.println();
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, out, isStrict);
        out.println();
        printErrors(errors);
        printSnippets(snippets);
    }

    private void printErrors(Iterable<Throwable> errors) {
        for (Throwable error : errors) {
            error.printStackTrace(out);
            out.println();
        }
    }

    private void printSnippets(List<String> snippets) {
        if (!snippets.isEmpty()) {
            out.append("\n");
            out.println("You can implement missing steps with the snippets below:");
            out.println();
            for (String snippet : snippets) {
                out.println(snippet);
            }
        }
    }
}
