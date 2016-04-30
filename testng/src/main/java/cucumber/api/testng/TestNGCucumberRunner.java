package cucumber.api.testng;

import cucumber.api.SummaryPrinter;
import cucumber.runtime.*;
import cucumber.runtime.Runtime;
import cucumber.runtime.io.MultiLoader;
import cucumber.runtime.io.ResourceLoader;
import cucumber.runtime.io.ResourceLoaderClassFinder;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.model.RunResult;
import gherkin.formatter.Formatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Glue code for running Cucumber via TestNG.
 */
public class TestNGCucumberRunner {
    private Runtime runtime;
    private List<Throwable> errors = new ArrayList<Throwable>();
    private UndefinedStepsTracker tracker = new UndefinedStepsTracker();
    private RuntimeOptions runtimeOptions;
    private ResourceLoader resourceLoader;
    private FeatureResultListener resultListener;
    private ClassLoader classLoader;
    private RunResult runResult = RunResult.IDENTITY;

    /**
     * Bootstrap the cucumber runtime
     *
     * @param clazz Which has the cucumber.api.CucumberOptions and org.testng.annotations.Test annotations
     */
    public TestNGCucumberRunner(Class clazz) {
        classLoader = clazz.getClassLoader();
        resourceLoader = new MultiLoader(classLoader);

        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        runtimeOptions = runtimeOptionsFactory.create();

        TestNgReporter reporter = new TestNgReporter(System.out);
        ClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
        resultListener = new FeatureResultListener(runtimeOptions.reporter(classLoader), runtimeOptions.isStrict());
        runtime = new Runtime(resourceLoader, classFinder, classLoader, runtimeOptions.isDryRun(), runtimeOptions.getGlue());
    }

    /**
     * Run the Cucumber features
     */
    public void runCukes() {
        for (CucumberFeature cucumberFeature : getFeatures()) {
            final RunResult runResult = cucumberFeature.run(
                    runtimeOptions.formatter(classLoader),
                    resultListener,
                    runtime,
                    tracker);
            this.runResult = RunResult.append(this.runResult, runResult);
        }
        finish();
        if (!resultListener.isPassed()) {
            throw new CucumberException(resultListener.getFirstError());
        }
    }

    public void runCucumber(CucumberFeature cucumberFeature) {
        resultListener.startFeature();
        final RunResult runResult = cucumberFeature.run(
                runtimeOptions.formatter(classLoader),
                resultListener,
                runtime,
                tracker);
        this.runResult = RunResult.append(this.runResult, runResult);

        if (!resultListener.isPassed()) {
            throw new CucumberException(resultListener.getFirstError());
        }
    }

    public void finish() {
        Formatter formatter = runtimeOptions.formatter(classLoader);
        SummaryPrinter summaryPrinter = runtimeOptions.summaryPrinter(classLoader);
        Stats.StatsFormatOptions statsFormatOptions = new Stats.StatsFormatOptions(runtimeOptions.isMonochrome());

        formatter.done();
        formatter.close();
        summaryPrinter.print(statsFormatOptions, runResult.stats, errors, runtime.getSnippets(tracker, runtimeOptions.getSnippetType().getFunctionNameGenerator()), runtimeOptions.isStrict());
    }

    /**
     * @return List of detected cucumber features
     */
    public List<CucumberFeature> getFeatures() {
        return runtimeOptions.cucumberFeatures(resourceLoader);
    }

    /**
     * @return returns the cucumber features as a two dimensional array of
     * {@link CucumberFeatureWrapper} objects.
     */
    public Object[][] provideFeatures() {
        try {
            List<CucumberFeature> features = getFeatures();
            List<Object[]> featuresList = new ArrayList<Object[]>(features.size());
            for (CucumberFeature feature : features) {
                featuresList.add(new Object[]{new CucumberFeatureWrapperImpl(feature)});
            }
            return featuresList.toArray(new Object[][]{});
        } catch (CucumberException e) {
            return new Object[][]{new Object[]{new CucumberExceptionWrapper(e)}};
        }
    }

}
