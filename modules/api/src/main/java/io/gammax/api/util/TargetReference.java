package io.gammax.api.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface TargetReference {
    Class<?> owner() default void.class;

    String name() default "";

    Signature signature() default @Signature;
}
