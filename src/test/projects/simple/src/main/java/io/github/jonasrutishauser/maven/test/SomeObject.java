package io.github.jonasrutishauser.maven.test;

import org.jspecify.annotations.Nullable;

public class SomeObject {

    private String someField;

    public SomeObject(String someField) {
        this.someField = someField;
    }

    public String getSomeField() {
        return someField;
    }

    public void setSomeField(@Nullable String someField) {
        this.someField = someField;
    }

}