package io.gammax.api;

import io.gammax.api.enums.Instruction;
import io.gammax.api.util.Signature;
import io.gammax.api.enums.Mode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String method();

    Instruction instruction();

    Mode mode();

    Signature signature() default @Signature;

    int index() default 0;
}
