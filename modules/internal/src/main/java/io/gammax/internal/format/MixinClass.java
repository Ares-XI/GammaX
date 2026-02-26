package io.gammax.internal.format;

import io.gammax.api.Mixin;
import io.gammax.internal.MixinRegistry;
import io.gammax.internal.format.data.ShadowField;
import io.gammax.internal.format.data.ShadowMethod;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;

public class MixinClass {
    private final Class<?> mixinClazz;

    private final Class<?> targetClass;

    public final ShadowField[] shadowFields;

    public final UniqueField[] uniqueFields;

    public final ShadowMethod[] shadowMethods;

    public final UniqueMethod[] uniqueMethods;

    public final InjectMethod[] injectMethods;

    private final boolean isValid;

    private final Class<?>[] interfaces;

    public MixinClass(
            @NotNull Class<?> clazz,
            @NotNull ShadowField[] shadowFields,
            @NotNull UniqueField[] uniqueFields,
            @NotNull ShadowMethod[] shadowMethods,
            @NotNull UniqueMethod[] uniqueMethods,
            @NotNull InjectMethod[] injectMethods,
            Class<?>[] interfaces
    ) {
        boolean add = false;

        if(clazz.isAnnotation() || clazz.isInterface() || clazz.isEnum()) new IllegalArgumentException("@Mixin class must be abstract").printStackTrace(System.err);
        if(!Modifier.isAbstract(clazz.getModifiers())) new IllegalArgumentException("@Mixin class must be abstract").printStackTrace(System.err);
        if(!clazz.isAnnotationPresent(Mixin.class)) new IllegalArgumentException("class must be annotated by @Mixin").printStackTrace(System.err);
        else add = true;

        this.mixinClazz = clazz;
        this.targetClass = clazz.getAnnotation(Mixin.class).value();
        this.shadowFields = shadowFields;
        this.uniqueFields = uniqueFields;
        this.shadowMethods = shadowMethods;
        this.uniqueMethods = uniqueMethods;
        this.injectMethods = injectMethods;
        this.interfaces = interfaces;

        try {
            MixinRegistry.getMixinClassLoader().loadClass(mixinClazz.getName());
            MixinRegistry.getMixinClassLoader().loadClass(targetClass.getName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace(System.err);
            add = false;
        }

        isValid = add;
    }

    public Class<?> getMixinClass() {
        return mixinClazz;
    }

    public Class<?>[] getInterfaces() {
        return interfaces;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }

    public boolean isValid() {
        return isValid;
    }
}
