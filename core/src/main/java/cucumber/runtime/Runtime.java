package cucumber.runtime;

import cucumber.api.Pending;
import cucumber.api.StepDefinitionReporter;
import cucumber.api.SummaryPrinter;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.xstream.LocalizedXStreams;
import gherkin.I18n;
import gherkin.formatter.Argument;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Comment;
import gherkin.formatter.model.DataTableRow;
import gherkin.formatter.model.DocString;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * This is the main entry point for running Cucumber features.
 */
public class Runtime implements UnreportedStepExecutor {

    private static final String[] PENDING_EXCEPTIONS = {
            "org.junit.AssumptionViolatedException",
            "org.junit.internal.AssumptionViolatedException"
    };

    static {
        Arrays.sort(PENDING_EXCEPTIONS);
    }

    private static final Object DUMMY_ARG = new Object();
    private static final byte ERRORS = 0x1;


    private final Stats.StatsFormatOptions statsFormatOptions;

    private final Glue glue;
    private final RuntimeOptions runtimeOptions;

    private final Collection<? extends Backend> backends;
    private final ResourceLoader resourceLoader;
    private final ClassLoader classLoader;
    private final StopWatch.StopWatchFactory stopWatchFactory;


    public Runtime(ResourceLoader resourceLoader, ClassFinder classFinder, ClassLoader classLoader, RuntimeOptions runtimeOptions) {
        this(resourceLoader, classLoader, loadBackends(resourceLoader, classFinder), runtimeOptions);
    }

    public Runtime(ResourceLoader resourceLoader, ClassLoader classLoader, Collection<? extends Backend> backends, RuntimeOptions runtimeOptions) {
        this(resourceLoader, classLoader, backends, runtimeOptions, StopWatch.SIMPLE_FACTORY, null);
    }

    public Runtime(ResourceLoader resourceLoader, ClassLoader classLoader, Collection<? extends Backend> backends,
                   RuntimeOptions runtimeOptions, RuntimeGlue optionalGlue) {
        this(resourceLoader, classLoader, backends, runtimeOptions, StopWatch.SIMPLE_FACTORY, optionalGlue);
    }

    public Runtime(ResourceLoader resourceLoader, ClassLoader classLoader, Collection<? extends Backend> backends,
                   RuntimeOptions runtimeOptions, StopWatch.StopWatchFactory stopWatchFactory, RuntimeGlue optionalGlue) {
        if (backends.isEmpty()) {
            throw new CucumberException("No backends were found. Please make sure you have a backend module on your CLASSPATH.");
        }
        this.resourceLoader = resourceLoader;
        this.classLoader = classLoader;
        this.backends = backends;
        this.runtimeOptions = runtimeOptions;
        this.stopWatchFactory = stopWatchFactory;
        this.glue = optionalGlue != null ? optionalGlue : new RuntimeGlue(new LocalizedXStreams(classLoader));
        this.statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());

        for (Backend backend : backends) {
            backend.loadGlue(glue, runtimeOptions.getGlue());
            backend.setUnreportedStepExecutor(this);
        }
    }

    private static Collection<? extends Backend> loadBackends(ResourceLoader resourceLoader, ClassFinder classFinder) {
        Reflections reflections = new Reflections(classFinder);
        return reflections.instantiateSubclasses(Backend.class, "cucumber.runtime", new Class[]{ResourceLoader.class}, new Object[]{resourceLoader});
    }

    public static final class RuntimeRunResult {
        public final byte exitStatus;
        public final List<Throwable> errors;

        public RuntimeRunResult(byte exitCode, List<Throwable> errors) {
            this.exitStatus = exitCode;
            this.errors = errors;
        }
    }

    /**
     * This is the main entry point. Used from CLI, but not from JUnit.
     */
    public RuntimeRunResult run() throws IOException {
        // Make sure all features parse before initialising any reporters/formatters
        List<CucumberFeature> features = runtimeOptions.cucumberFeatures(resourceLoader);
        Stats stats = new Stats();
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();

        // TODO: This is duplicated in cucumber.api.android.CucumberInstrumentationCore - refactor or keep uptodate

        Formatter formatter = runtimeOptions.formatter(classLoader);
        Reporter reporter = runtimeOptions.reporter(classLoader);
        StepDefinitionReporter stepDefinitionReporter = runtimeOptions.stepDefinitionReporter(classLoader);

        glue.reportStepDefinitions(stepDefinitionReporter);

        for (CucumberFeature cucumberFeature : features) {
            cucumberFeature.run(formatter, reporter, this, stats, errors, tracker);
        }

        formatter.done();
        formatter.close();
        printSummary(stats, errors, tracker);
        return new RuntimeRunResult(exitStatus(errors, tracker), errors);
    }

    public void printSummary(Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker) {
        SummaryPrinter summaryPrinter = runtimeOptions.summaryPrinter(classLoader);
        summaryPrinter.print(this, stats, errors, tracker);
    }

    void printStats(Stats stats, PrintStream out) {
        Stats.StatsFormatter.printStats(stats, statsFormatOptions, out, runtimeOptions.isStrict());
    }

    public ScenarioImpl buildBackendWorlds(Reporter reporter, Set<Tag> tags, gherkin.formatter.model.Scenario gherkinScenario) {
        for (Backend backend : backends) {
            backend.buildWorld();
        }
        //TODO: this is the initial state of the state machine, it should not go here, but into something else
        return new ScenarioImpl(reporter, tags, gherkinScenario);
    }

    public void disposeBackendWorlds() {
        for (Backend backend : backends) {
            backend.disposeWorld();
        }
    }


    public byte exitStatus(List<Throwable> errors, UndefinedStepsTracker undefinedStepsTracker) {
        byte result = 0x0;
        if (hasErrors(errors) || hasUndefinedOrPendingStepsAndIsStrict(errors, undefinedStepsTracker)) {
            result |= ERRORS;
        }
        return result;
    }

    private boolean hasUndefinedOrPendingStepsAndIsStrict(List<Throwable> errors, UndefinedStepsTracker undefinedStepsTracker) {
        return runtimeOptions.isStrict() && hasUndefinedOrPendingSteps(errors, undefinedStepsTracker);
    }

    private boolean hasUndefinedOrPendingSteps(List<Throwable> throwables, UndefinedStepsTracker undefinedStepsTracker) {
        return undefinedStepsTracker.hasUndefinedSteps() || hasPendingSteps(throwables);
    }


    private boolean hasPendingSteps(List<Throwable> errors) {
        return !errors.isEmpty() && !hasErrors(errors);
    }

    private boolean hasErrors(List<Throwable> errors) {
        for (Throwable error : errors) {
            if (!isPending(error)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getSnippets(UndefinedStepsTracker undefinedStepsTracker) {
        return undefinedStepsTracker.getSnippets(backends, runtimeOptions.getSnippetType().getFunctionNameGenerator());
    }

    public Glue getGlue() {
        return glue;
    }

    public boolean runBeforeHooks(ScenarioImpl scenarioResult, Stats stats, List<Throwable> errors, Reporter reporter, Set<Tag> tags) {
        return runHooks(scenarioResult, stats, errors, glue.getBeforeHooks(), reporter, tags, true, runtimeOptions.isDryRun());
    }

    public boolean runAfterHooks(ScenarioImpl scenarioResult, Stats stats, List<Throwable> errors, Reporter reporter, Set<Tag> tags) {
        return runHooks(scenarioResult, stats, errors, glue.getAfterHooks(), reporter, tags, false, runtimeOptions.isDryRun());
    }

    private boolean runHooks(ScenarioImpl scenarioResult, Stats stats, List<Throwable> errors, List<HookDefinition> hooks, Reporter reporter, Set<Tag> tags, boolean isBefore, boolean isDryRun) {
        boolean skipNextStep = false;
        if (!isDryRun) {
            for (HookDefinition hook : hooks) {
                if (runHookIfTagsMatch(scenarioResult, stats, hook, reporter, tags, errors, isBefore)) {
                    skipNextStep = true;
                }
            }
        }
        return skipNextStep;
    }

    private boolean runHookIfTagsMatch(ScenarioImpl scenarioResult, Stats stats, HookDefinition hook, Reporter reporter, Set<Tag> tags, List<Throwable> errors, boolean isBefore) {
        boolean skipNextStep = false;
        if (hook.matches(tags)) {
            String status = Result.PASSED;
            Throwable error = null;
            Match match = new Match(Collections.<Argument>emptyList(), hook.getLocation(false));
            final StopWatch stopWatch = stopWatchFactory.create();
            stopWatch.start();
            try {
                hook.execute(scenarioResult);
            } catch (Throwable t) {
                error = t;
                status = isPending(t) ? "pending" : Result.FAILED;
                errors.add(t);
                skipNextStep = true;
            } finally {
                long duration = stopWatch.stop();
                Result result = new Result(status, duration, error, DUMMY_ARG);
                scenarioResult.add(result);
                stats.addHookTime(result.getDuration());
                if (isBefore) {
                    reporter.before(match, result);
                } else {
                    reporter.after(match, result);
                }
            }
        }
        return skipNextStep;
    }

    //TODO: Maybe this should go into the cucumber step execution model and it should return the result of that execution!
    @Override
    public void runUnreportedStep(String featurePath, I18n i18n, String stepKeyword, String stepName, int line, List<DataTableRow> dataTableRows, DocString docString, UndefinedStepsTracker undefinedStepsTracker) throws Throwable {
        Step step = new Step(Collections.<Comment>emptyList(), stepKeyword, stepName, line, dataTableRows, docString);

        StepDefinitionMatch match = glue.stepDefinitionMatch(featurePath, step, i18n, undefinedStepsTracker);
        if (match == null) {
            UndefinedStepException error = new UndefinedStepException(step);

            StackTraceElement[] originalTrace = error.getStackTrace();
            StackTraceElement[] newTrace = new StackTraceElement[originalTrace.length + 1];
            newTrace[0] = new StackTraceElement("✽", "StepDefinition", featurePath, line);
            System.arraycopy(originalTrace, 0, newTrace, 1, originalTrace.length);
            error.setStackTrace(newTrace);

            throw error;
        }
        match.runStep(i18n);
    }

    /** returns {@code true} if the next step should be skipped */
    public boolean runStep(ScenarioImpl scenarioResult, Stats stats, List<Throwable> errors, UndefinedStepsTracker tracker, String featurePath, Step step, Reporter reporter, I18n i18n, boolean skip) {
        final StepDefinitionMatch match;

        try {
            match = glue.stepDefinitionMatch(featurePath, step, i18n, tracker);
        } catch (AmbiguousStepDefinitionsException e) {
            reporter.match(e.getMatches().get(0));
            Result result = new Result(Result.FAILED, 0L, e, DUMMY_ARG);
            reporter.result(result);
            scenarioResult.add(result);
            stats.addStep(result);
            errors.add(e);
            return true;
        }

        if (match == null) {
            reporter.match(Match.UNDEFINED);
            reporter.result(Result.UNDEFINED);
            scenarioResult.add(Result.UNDEFINED);
            stats.addStep(Result.UNDEFINED);
            return true;
        }

        reporter.match(match);

        if (skip || runtimeOptions.isDryRun()) {
            scenarioResult.add(Result.SKIPPED);
            stats.addStep(Result.SKIPPED);
            reporter.result(Result.SKIPPED);
            return true;

        } else {
            String status = Result.PASSED;
            Throwable error = null;
            final StopWatch stopWatch = stopWatchFactory.create();
            stopWatch.start();
            boolean skipNextStep = skip;

            try {
                match.runStep(i18n);
            } catch (Throwable t) {
                error = t;
                status = isPending(t) ? "pending" : Result.FAILED;
                errors.add(t);
                skipNextStep = true;
            } finally {
                long duration = stopWatch.stop();
                Result result = new Result(status, duration, error, DUMMY_ARG);
                scenarioResult.add(result);
                stats.addStep(result);
                reporter.result(result);
            }
            return skipNextStep;
        }
    }

    public static boolean isPending(Throwable t) {
        if (t == null) {
            return false;
        }
        return t.getClass().isAnnotationPresent(Pending.class) || Arrays.binarySearch(PENDING_EXCEPTIONS, t.getClass().getName()) >= 0;
    }

}
