package cucumber.runtime;

import cucumber.api.Pending;
import cucumber.api.StepDefinitionReporter;
import cucumber.api.SummaryPrinter;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.RunResult;
import cucumber.runtime.snippets.FunctionNameGenerator;
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


    private final Glue glue;

    private final Collection<? extends Backend> backends;
    private final ResourceLoader resourceLoader;
    private final ClassLoader classLoader;
    private final StopWatch.StopWatchFactory stopWatchFactory;
    private final boolean isDryRun;


    public Runtime(ResourceLoader resourceLoader, ClassFinder classFinder, ClassLoader classLoader, boolean isDryRun, List<String> glue) {
        this(resourceLoader, classLoader, isDryRun, glue, loadBackends(resourceLoader, classFinder));
    }

    public Runtime(ResourceLoader resourceLoader, ClassLoader classLoader, boolean isDryRun, List<String> glue, Collection<? extends Backend> backends) {
        this(resourceLoader, classLoader, isDryRun, glue, backends, StopWatch.SIMPLE_FACTORY, null);
    }

    public Runtime(ResourceLoader resourceLoader, ClassLoader classLoader, boolean isDryRun, List<String> glue, Collection<? extends Backend> backends, RuntimeGlue optionalGlue) {
        this(resourceLoader, classLoader, isDryRun, glue, backends, StopWatch.SIMPLE_FACTORY, optionalGlue);
    }

    public Runtime(ResourceLoader resourceLoader, ClassLoader classLoader,
                   boolean isDryRun, List<String> glue,
                   Collection<? extends Backend> backends, StopWatch.StopWatchFactory stopWatchFactory, RuntimeGlue optionalGlue) {
        if (backends.isEmpty()) {
            throw new CucumberException("No backends were found. Please make sure you have a backend module on your CLASSPATH.");
        }
        this.resourceLoader = resourceLoader;
        this.classLoader = classLoader;
        this.backends = backends;
        this.stopWatchFactory = stopWatchFactory;
        this.glue = optionalGlue != null ? optionalGlue : new RuntimeGlue(new LocalizedXStreams(classLoader));
        this.isDryRun = isDryRun;

        for (Backend backend : backends) {
            backend.loadGlue(this.glue, glue);
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
    public RuntimeRunResult run(RuntimeOptions runtimeOptions) throws IOException {
        // Make sure all features parse before initialising any reporters/formatters
        List<CucumberFeature> features = runtimeOptions.cucumberFeatures(resourceLoader);
        RunResult runResult = RunResult.IDENTITY;
        List<Throwable> errors = new ArrayList<Throwable>();
        UndefinedStepsTracker tracker = new UndefinedStepsTracker();
        final Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());

        // TODO: This is duplicated in cucumber.api.android.CucumberInstrumentationCore - refactor or keep uptodate

        Formatter formatter = runtimeOptions.formatter(classLoader);
        Reporter reporter = runtimeOptions.reporter(classLoader);
        StepDefinitionReporter stepDefinitionReporter = runtimeOptions.stepDefinitionReporter(classLoader);

        glue.reportStepDefinitions(stepDefinitionReporter);

        for (CucumberFeature cucumberFeature : features) {
            final RunResult runFeatureResult = cucumberFeature.run(formatter, reporter, this, tracker);
            runResult = RunResult.append(runResult, runFeatureResult);
        }

        formatter.done();
        formatter.close();
        SummaryPrinter summaryPrinter = runtimeOptions.summaryPrinter(classLoader);
        summaryPrinter.print(statsFormatOptions, runResult.stats, runResult.errors, getSnippets(tracker, runtimeOptions.getSnippetType().getFunctionNameGenerator()), runtimeOptions.isStrict());
        final byte exitStatus = Runtime.exitStatus(errors, tracker, runtimeOptions.isStrict());
        return new RuntimeRunResult(exitStatus, errors);
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


    public static byte exitStatus(List<Throwable> errors, UndefinedStepsTracker undefinedStepsTracker, boolean isStrict) {
        byte result = 0x0;
        if (hasErrors(errors) || hasUndefinedOrPendingStepsAndIsStrict(errors, undefinedStepsTracker, isStrict)) {
            result |= ERRORS;
        }
        return result;
    }

    private static boolean hasUndefinedOrPendingStepsAndIsStrict(List<Throwable> errors, UndefinedStepsTracker undefinedStepsTracker, boolean isStrict) {
        return isStrict && hasUndefinedOrPendingSteps(errors, undefinedStepsTracker);
    }

    private static boolean hasUndefinedOrPendingSteps(List<Throwable> throwables, UndefinedStepsTracker undefinedStepsTracker) {
        return undefinedStepsTracker.hasUndefinedSteps() || hasPendingSteps(throwables);
    }


    private static boolean hasPendingSteps(List<Throwable> errors) {
        return !errors.isEmpty() && !hasErrors(errors);
    }

    private static boolean hasErrors(List<Throwable> errors) {
        for (Throwable error : errors) {
            if (!isPending(error)) {
                return true;
            }
        }
        return false;
    }

    public List<String> getSnippets(UndefinedStepsTracker undefinedStepsTracker, FunctionNameGenerator functionNameGenerator) {
        return undefinedStepsTracker.getSnippets(backends, functionNameGenerator);
    }

    public Glue getGlue() {
        return glue;
    }

    public RunStepResult runBeforeHooks(ScenarioImpl scenarioResult, Reporter reporter, Set<Tag> tags) {
        return runHooks(scenarioResult, glue.getBeforeHooks(), reporter, tags, true, isDryRun);
    }

    public RunStepResult runAfterHooks(ScenarioImpl scenarioResult, Reporter reporter, Set<Tag> tags) {
        return runHooks(scenarioResult, glue.getAfterHooks(), reporter, tags, false, isDryRun);
    }

    private RunStepResult runHooks(ScenarioImpl scenarioResult, List<HookDefinition> hooks, Reporter reporter, Set<Tag> tags, boolean isBefore, boolean isDryRun) {
        boolean skipNextStep = false;
        Stats stats = Stats.IDENTITY;
        List<Throwable> errors = Collections.emptyList();
        if (!isDryRun) {
            for (HookDefinition hook : hooks) {
                final RunStepResult runHookResult = runHookIfTagsMatch(scenarioResult, hook, reporter, tags, isBefore);
                stats = Stats.append(stats, runHookResult.runResult.stats);
                errors = Utils.append(errors, runHookResult.runResult.errors);
                if (runHookResult.skipNext) {
                    skipNextStep = true;
                }
            }
        }
        return new RunStepResult(skipNextStep, new RunResult(stats, errors));
    }

    private RunStepResult runHookIfTagsMatch(ScenarioImpl scenarioResult, HookDefinition hook, Reporter reporter, Set<Tag> tags, boolean isBefore) {
        Stats stats = new Stats();
        boolean skipNextStep = false;
        final List<Throwable> errors = new ArrayList<Throwable>(1);
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
        return new RunStepResult(skipNextStep, new RunResult(stats, errors));
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

    public static final class RunStepResult {
        public final boolean skipNext;
        public final RunResult runResult;

        public RunStepResult(boolean skipNext, RunResult runResult) {
            this.skipNext = skipNext;
            this.runResult = runResult;
        }
    }

    /** returns {@code true} if the next step should be skipped */
    public RunStepResult runStep(ScenarioImpl scenarioResult, UndefinedStepsTracker tracker, String featurePath, Step step, Reporter reporter, I18n i18n, boolean skip) {
        final StepDefinitionMatch match;
        final Stats stats = new Stats();
        try {
            match = glue.stepDefinitionMatch(featurePath, step, i18n, tracker);
        } catch (AmbiguousStepDefinitionsException e) {
            reporter.match(e.getMatches().get(0));
            Result result = new Result(Result.FAILED, 0L, e, DUMMY_ARG);
            reporter.result(result);
            scenarioResult.add(result);
            stats.addStep(result);
            return new RunStepResult(true, new RunResult(stats, Collections.<Throwable>singletonList(e)));
        }

        if (match == null) {
            reporter.match(Match.UNDEFINED);
            reporter.result(Result.UNDEFINED);
            scenarioResult.add(Result.UNDEFINED);
            stats.addStep(Result.UNDEFINED);
            return new RunStepResult(true, new RunResult(stats, Collections.<Throwable>emptyList()));
        }

        reporter.match(match);

        if (skip || isDryRun) {
            scenarioResult.add(Result.SKIPPED);
            stats.addStep(Result.SKIPPED);
            reporter.result(Result.SKIPPED);
            return new RunStepResult(true, new RunResult(stats, Collections.<Throwable>emptyList()));

        } else {
            String status = Result.PASSED;
            Throwable error = null;
            final StopWatch stopWatch = stopWatchFactory.create();
            stopWatch.start();
            boolean skipNextStep = false;
            final List<Throwable> errors = new ArrayList<Throwable>(1);

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
            return new RunStepResult(skipNextStep, new RunResult(stats, errors));
        }
    }

    public static boolean isPending(Throwable t) {
        if (t == null) {
            return false;
        }
        return t.getClass().isAnnotationPresent(Pending.class) || Arrays.binarySearch(PENDING_EXCEPTIONS, t.getClass().getName()) >= 0;
    }

}
