package cucumber.runtime.model;

import cucumber.runtime.Utils;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public final class ExecutionResult {

    public static ExecutionResult EMPTY =
            new ExecutionResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Optional.empty(), false);

    /**
     * Util to break out of the ExecutionResult chain and executeAll it sequentially, executing side effects on reporters and formatters as they occur.
     * This can be removed after migration.
     */
    public static void executeAll(ExecutionResult executionResult, Reporter reporter, gherkin.formatter.Formatter formatter) {
        do {
            executionResult.formatterActions.forEach(a->a.apply(formatter));
            executionResult.reporterActions.forEach(a->a.apply(reporter));
            if (executionResult.nextStep.isPresent()) {
                executionResult = executionResult.nextStep.get().apply();
            }
        } while (executionResult.nextStep.isPresent());
    }

    public ExecutionResult withFormatterActions(Iterable<FormatterAction> formatterActions) {
        return new ExecutionResult(formatterActions, reporterActions, errors, nextStep, skipNextStep);
    }

    public ExecutionResult withReporterActions(Iterable<ReporterAction> reporterActions) {
        return new ExecutionResult(formatterActions, reporterActions, errors, nextStep, skipNextStep);
    }

    public ExecutionResult withErrors(Collection<Throwable> errors) {
        return new ExecutionResult(formatterActions, reporterActions, errors, nextStep, skipNextStep);
    }

    public ExecutionResult withError(Throwable error) {
        return new ExecutionResult(formatterActions, reporterActions, Utils.concat(errors, Collections.singletonList(error)), nextStep, skipNextStep);
    }

    public ExecutionResult done() {
        return new ExecutionResult(formatterActions,reporterActions,errors,Optional.empty(), skipNextStep);
    }

    public ExecutionResult withSkipNextStep(boolean skipNextStep) {
        return new ExecutionResult(formatterActions,reporterActions,errors,Optional.empty(), skipNextStep);
    }

    public ExecutionResult withContinuation(Continuation continuation) {
        return new ExecutionResult(formatterActions,reporterActions,errors,Optional.of(continuation), skipNextStep);
    }

    public ExecutionResult skipNextStep() {
        return new ExecutionResult(formatterActions,reporterActions,errors,Optional.empty(), true);
    }



    public ExecutionResult(Iterable<FormatterAction> formatterActions,
                           Iterable<ReporterAction> reporterActions,
                           Collection<Throwable> errors,
                           Optional<Continuation> nextStep, boolean skipNextStep) {
        this.formatterActions = formatterActions;
        this.reporterActions = reporterActions;
        this.errors = errors;
        this.nextStep = nextStep;
        this.skipNextStep = skipNextStep;
    }

    public interface FormatterAction { void apply(Formatter formatter); }
    public interface Continuation { ExecutionResult apply(); }
    public interface ReporterAction { void apply(Reporter reporter); }

    public final Iterable<FormatterAction> formatterActions;
    public final Iterable<ReporterAction> reporterActions;
    public final Collection<Throwable> errors;
    public final Optional<Continuation> nextStep;
    public final boolean skipNextStep;

}
