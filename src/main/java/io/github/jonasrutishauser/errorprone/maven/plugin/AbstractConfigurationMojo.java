package io.github.jonasrutishauser.errorprone.maven.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

abstract class AbstractConfigurationMojo extends AbstractMojo {

    /**
     * Allows disabling Error Prone altogether.
     * <p>
     * Error Prone will still be in the annotation processor path, but
     * {@code -Xplugin:ErrorProne} won't be passed as a compiler argument.
     */
    @Parameter(defaultValue = "true", property = "errorprone.enabled")
    private boolean enabled;

    /**
     * Disable all Error Prone checks. Maps to {@code -XepDisableAllChecks}.
     * <p>
     * This will be the first argument, so checks can then be re-enabled on a
     * case-by-case basis.
     */
    @Parameter(defaultValue = "false", property = "errorprone.disableAllChecks")
    private boolean disableAllChecks;

    /**
     * Disables all Error Prone warnings. Maps to {@code -XepDisableAllWarnings}.
     * <p>
     * This will be among the first arguments, so checks can then be re-enabled on a
     * case-by-case basis.
     */
    @Parameter(defaultValue = "false", property = "errorprone.disableAllWarnings")
    private boolean disableAllWarnings;

    /**
     * Turns all Error Prone errors into warnings. Maps to
     * {@code -XepAllErrorsAsWarnings}.
     * <p>
     * This will be among the first arguments, so checks can then be promoted back
     * to error on a case-by-case basis.
     */
    @Parameter(defaultValue = "false", property = "errorprone.allErrorsAsWarnings")
    private boolean allErrorsAsWarnings;

    /**
     * Turn all Error Prone suggestions into warnings. Maps to
     * {@code -XepAllSuggestionsAsWarnings}.
     * <p>
     * This will be among the first arguments, so checks can then be demoted back to
     * suggestions on a case-by-case basis.
     */
    @Parameter(defaultValue = "false", property = "errorprone.allSuggestionsAsWarnings")
    private boolean allSuggestionsAsWarnings;

    /**
     * Enables all Error Prone checks, checks that are disabled by default are
     * enabled as warnings. Maps to {@code -XepAllDisabledChecksAsWarnings}.
     * <p>
     * This will be among the first arguments, so checks can then be disabled again
     * on a case-by-case basis.
     */
    @Parameter(defaultValue = "false", property = "errorprone.allDisabledChecksAsWarnings")
    private boolean allDisabledChecksAsWarnings;

    /**
     * Disables warnings in classes annotated with {@code @Generated}. Maps to
     * {@code -XepDisableWarningsInGeneratedCode}.
     */
    @Parameter(defaultValue = "false", property = "errorprone.disableWarningsInGeneratedCode")
    private boolean disableWarningsInGeneratedCode;

    /**
     * Tells Error Prone to ignore unknown check names in {@link #checks checks}.
     * Maps to {@code -XepIgnoreUnknownCheckNames}.
     */
    @Parameter(defaultValue = "false", property = "errorprone.ignoreUnknownCheckNames")
    private boolean ignoreUnknownCheckNames;

    /**
     * Ignores suppression annotations, such as
     * {@link SuppressWarnings @SuppressWarnings}. Maps to
     * {@code -XepIgnoreSuppressionAnnotations}.
     */
    @Parameter(defaultValue = "false", property = "errorprone.ignoreSuppressionAnnotations")
    private boolean ignoreSuppressionAnnotations;

    /**
     * A regular expression pattern of file paths to exclude from Error Prone
     * checking. Maps to {@code -XepExcludedPaths}.
     */
    @Parameter(property = "errorprone.excludePaths")
    private String excludePaths;

    /**
     * A map of check name to {@link CheckSeverity}, to configure which checks are
     * enabled or disabled, and their severity.
     * <p>
     * Maps each entry to {@code -Xep:<key>:<value>}, or {@code -Xep:<key>} if the
     * value is {@link CheckSeverity#DEFAULT}.
     */
    @Parameter
    private Map<String, CheckSeverity> checks = new HashMap<>();

    /**
     * A map of <a href=
     * "https://errorprone.info/docs/flags#pass-additional-info-to-bugcheckers">check
     * options</a> to their value.
     * <p>
     * Use an explicit {@code "true"} value for a boolean option.
     * <p>
     * Maps each entry to {@code -XepOpt:<key>=<value>}.
     */
    @Parameter
    private Map<String, String> options = new HashMap<>();

    /**
     * A map of <a href=
     * "https://errorprone.info/docs/flags#pass-additional-info-to-bugcheckers">namespaced
     * check options</a> to their value.
     * <p>
     * Use an explicit {@code "true"} value for a boolean option.
     * <p>
     * Maps each entry to {@code -XepOpt:<namespace>:<key>=<value>}.
     */
    @Parameter
    private Map<String, Map<String, String>> namespacedOptions = new HashMap<>();

    /**
     * Additional arguments passed to Error Prone.
     */
    @Parameter(property = "errorprone.arguments")
    private List<String> arguments = new ArrayList<>();

    /**
     * The name of the {@link MavenProject#getProperties() project property} which
     * will be set to the generated compiler argument (the value will be of the form
     * {@code -Xplugin:ErrorProne <options>}).
     */
    @Parameter(defaultValue = "errorprone.compile.argument")
    private String propertyName;

    @Inject
    private MavenProject project;

    @Inject
    private CompilerConfiguration compilerConfiguration;

    @Override
    public void execute() throws MojoExecutionException {
        String propertyValue = "";
        if (enabled) {
            List<String> options = getOptions();

            propertyValue = "-Xplugin:ErrorProne " + String.join(" ", options);
        }
        getLog().debug("Setting project property \"" + propertyName + "\" to \"" + propertyValue + "\".");
        project.getProperties().put(propertyName, propertyValue);
        compilerConfiguration.configure(project, propertyName);
    }

    String getPropertyName() {
        return propertyName;
    }

    private List<String> getOptions() throws MojoExecutionException {
        List<String> options = new ArrayList<>();
        maybeAddOption(options, "-XepDisableAllChecks", disableAllChecks);
        maybeAddOption(options, "-XepDisableAllWarnings", disableAllWarnings);
        maybeAddOption(options, "-XepAllErrorsAsWarnings", allErrorsAsWarnings);
        maybeAddOption(options, "-XepAllSuggestionsAsWarnings", allSuggestionsAsWarnings);
        maybeAddOption(options, "-XepAllDisabledChecksAsWarnings", allDisabledChecksAsWarnings);
        maybeAddOption(options, "-XepDisableWarningsInGeneratedCode", disableWarningsInGeneratedCode);
        maybeAddOption(options, "-XepIgnoreUnknownCheckNames", ignoreUnknownCheckNames);
        maybeAddOption(options, "-XepIgnoreSuppressionAnnotations", ignoreSuppressionAnnotations);
        maybeAddOption(options, "-XepCompilingTestOnlyCode", isCompilingTestOnlyCode());
        maybeAddOption(options, "-XepExcludedPaths", excludePaths);

        for (var entry : checks.entrySet()) {
            validateName(entry.getKey());
            String option = "-Xep:" + entry.getKey();
            if (entry.getValue() != CheckSeverity.DEFAULT) {
                option += ":" + entry.getValue().name();
            }
            options.add(option);
        }
        for (var namespacedEntry : this.namespacedOptions.entrySet()) {
            for (var entry : namespacedEntry.getValue().entrySet()) {
                options.add("-XepOpt:" + namespacedEntry.getKey() + ":" + entry.getKey() + "=" + entry.getValue());
            }
        }
        for (var entry : this.options.entrySet()) {
            options.add("-XepOpt:" + entry.getKey() + "=" + entry.getValue());
        }
        options.addAll(arguments);

        return options;
    }

    private void validateName(String checkName) throws MojoExecutionException {
        if (checkName.contains(":")) {
            throw new MojoExecutionException(
                    String.format("Error Prone check name cannot contain a colon (\":\"): \"%s\".", checkName));
        }
    }

    private void maybeAddOption(List<String> options, String option, boolean value) {
        if (value) {
            options.add(option);
        }
    }

    private void maybeAddOption(List<String> options, String option, String value) {
        if (value != null && !value.isBlank()) {
            options.add(option + ":" + value);
        }
    }

    protected abstract boolean isCompilingTestOnlyCode();

}
