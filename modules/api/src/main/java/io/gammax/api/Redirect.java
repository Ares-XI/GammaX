package io.gammax.api;

import io.gammax.api.enums.RedirectAt;
import io.gammax.api.util.Signature;
import io.gammax.api.enums.Mode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Redirect {
    String method();

    At at();

    Signature signature() default @Signature;

    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface At {
        RedirectAt type();

        Mode shift() default Mode.BEFORE;

        int by() default 0;
    }
}