package io.gammax.api;

import io.gammax.api.enums.InjectAt;
import io.gammax.api.util.Signature;
import io.gammax.api.enums.Mode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String method();

    At at();

    Signature signature() default @Signature;

    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface At {
        InjectAt value();

        Mode mode() default Mode.BEFORE;

        int index() default 0;
    }
}
