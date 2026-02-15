package io.github.jonasrutishauser.errorprone.maven.plugin;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test-configuration", defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES, threadSafe = true)
public class TestConfigurationMojo extends AbstractConfigurationMojo {

    /**
     * Tells Error Prone that the compilation contains only test code. Maps to
     * {@code -XepCompilingTestOnlyCode}.
     */
    @Parameter(defaultValue = "true")
    private boolean compilingTestOnlyCode;

    @Override
    protected boolean isCompilingTestOnlyCode() {
        return compilingTestOnlyCode;
    }

}
