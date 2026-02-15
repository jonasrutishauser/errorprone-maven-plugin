package io.github.jonasrutishauser.errorprone.maven.plugin;

import javax.inject.Named;

import org.apache.maven.artifact.handler.ArtifactHandler;

@Named("errorprone")
public class ErrorproneArtifactHandler implements ArtifactHandler {

    @Override
    public String getExtension() {
        return "jar";
    }

    @Override
    public String getDirectory() {
        return null;
    }

    @Override
    public String getClassifier() {
        return null;
    }

    @Override
    public String getPackaging() {
        return null;
    }

    @Override
    public boolean isIncludesDependencies() {
        return false;
    }

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public boolean isAddedToClasspath() {
        return false;
    }

}
