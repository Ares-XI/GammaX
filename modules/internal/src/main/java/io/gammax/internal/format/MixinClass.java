package io.gammax.internal.format;

import io.gammax.api.Mixin;
import io.gammax.internal.MixinRegistry;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class MixinClass {
    private final Class<?> mixinClazz;

    private final Class<?> targetClass;

    public final ShadowField[] shadowFields;

    public final UniqueField[] uniqueFields;

    public final ShadowMethod[] shadowMethods;

    public UniqueMethod[] uniqueMethods;

    public MixinClass(
            @NotNull Class<?> clazz,
            @NotNull ShadowField[] shadowFields,
            @NotNull UniqueField[] uniqueFields,
            @NotNull ShadowMethod[] shadowMethods,
            @NotNull UniqueMethod[] uniqueMethods
    ) {
        boolean add = false;

        if(clazz.isAnnotation() || clazz.isInterface() || clazz.isEnum()) new IllegalArgumentException("@Mixin class must be abstract").printStackTrace(System.err);
        if(!Modifier.isAbstract(clazz.getModifiers())) new IllegalArgumentException("@Mixin class must be abstract").printStackTrace(System.err);
        if(!clazz.isAnnotationPresent(Mixin.class)) new IllegalArgumentException("class must be annotated by @Mixin").printStackTrace(System.err);
        else add = true;

        this.mixinClazz = clazz;
        this.targetClass = clazz.getAnnotation(Mixin.class).target();
        this.shadowFields = shadowFields;
        this.uniqueFields = uniqueFields;
        this.shadowMethods = shadowMethods;
        this.uniqueMethods = uniqueMethods;

        try {
            MixinRegistry.getMixinClassLoader().loadClass(mixinClazz.getName());
            MixinRegistry.getMixinClassLoader().loadClass(targetClass.getName());
        } catch (ClassNotFoundException e) {
            e.printStackTrace(System.err);
            add = false;
        }

        if(add) {
            List<UniqueMethod> list = new ArrayList<>();
            for(Method method: MixinRegistry.getUniqueMethods()) {
                list.add(
                        new UniqueMethod(
                                method,
                                targetClass,
                                shadowFields,
                                shadowMethods,
                                uniqueFields
                        )
                );
            }
            this.uniqueMethods = list.toArray(new UniqueMethod[0]);
            MixinRegistry.getMixins().add(this);
        }
    }

    public Class<?> getMixinClass() {
        return mixinClazz;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }
}
