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
    public void print(cucumber.runtime.Runtime runtime, Stats stats) {
        out.println();
        printStats(runtime, stats);
        out.println();
        printErrors(runtime.getErrors());
        printSnippets(runtime.getSnippets());
    }

    private void printStats(cucumber.runtime.Runtime runtime, Stats stats) {
        runtime.printStats(stats, out);
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
