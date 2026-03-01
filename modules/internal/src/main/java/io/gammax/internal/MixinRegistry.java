package io.gammax.internal;

import io.gammax.api.*;
import io.gammax.internal.format.*;
import io.gammax.internal.format.data.ArgumentParameter;
import io.gammax.internal.format.data.LocalParameter;
import io.gammax.internal.format.data.ShadowField;
import io.gammax.internal.format.data.ShadowMethod;
import io.gammax.internal.jar.MixinJarRegister;
import io.gammax.internal.jar.MixinClassLoader;
import io.gammax.internal.json.MixinConfigFormat;
import io.gammax.internal.json.MixinJsonParser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class MixinRegistry {
    private static final Set<MixinClass> mixins = new HashSet<>();

    private static final MixinClassLoader loader = new MixinClassLoader();

    private static final MixinJarRegister jars = new MixinJarRegister();

    public static MixinClass[] getCache() {
        return mixins.toArray(new MixinClass[0]);
    }

    public static MixinClassLoader getMixinClassLoader() {
        return loader;
    }

    public static MixinJarRegister getMixinJarRegister() {
        return jars;
    }

    public static void clearCache() { mixins.clear(); }

    public static boolean isTargetPath(String className) {
        for(MixinClass mixin: MixinRegistry.getCache()) if(mixin.getTargetClass().getName().equals(className)) return true;
        return false;
    }

    public static void loadCache() {
        MixinJsonParser parser = new MixinJsonParser();
        List<MixinConfigFormat> parsed = parser.loadAllMixinConfigs();

        System.out.println("Find " + parsed.size() + " mixins.json files");

        if(parsed.toArray().length != 0) System.out.println("Start parsing");
        else {
            System.out.println("Skip parsing");
            return;
        }

        for(MixinConfigFormat format: parsed) {
            for(String path: format.mixins) {
                try {
                    System.out.println(path);
                    Class<?> clazz = loader.loadClass(path);

                    List<ShadowField> shadowFieldsList = new ArrayList<>();
                    List<ShadowMethod> shadowMethodsList = new ArrayList<>();
                    List<UniqueField> uniqueFieldsList = new ArrayList<>();
                    List<UniqueMethod> uniqueMethodsList = new ArrayList<>();
                    List<InjectMethod> injectMethodsList = new ArrayList<>();
                    Class<?>[] interfaces = clazz.getInterfaces();

                    List<Method> tempUniqueMethods = new ArrayList<>();
                    List<Method> tempInjectMethods = new ArrayList<>();
                    Map<Method, List<Parameter>> tempArgumentParameters = new HashMap<>();
                    Map<Method, List<Parameter>> tempLocalParameters = new HashMap<>();

                    for(Field field: clazz.getDeclaredFields()) {
                        if(field.isAnnotationPresent(Shadow.class) && field.isAnnotationPresent(Unique.class)) {
                            new IllegalArgumentException("field cannot be annotated by @Shadow and @Unique").printStackTrace(System.err);
                        }
                        if(field.isAnnotationPresent(Shadow.class)) shadowFieldsList.add(new ShadowField(field));
                        if(field.isAnnotationPresent(Unique.class)) uniqueFieldsList.add(new UniqueField(field));
                    }

                    for(Method method: clazz.getDeclaredMethods()) {
                        if(method.isAnnotationPresent(Shadow.class) && method.isAnnotationPresent(Unique.class)) {
                            new IllegalArgumentException("method cannot be annotated by @Shadow and @Unique").printStackTrace(System.err);
                        }
                        if(method.isAnnotationPresent(Shadow.class) && method.isAnnotationPresent(Inject.class)) {
                            new IllegalArgumentException("method cannot be annotated by @Shadow and @Inject").printStackTrace(System.err);
                        }
                        if(method.isAnnotationPresent(Unique.class) && method.isAnnotationPresent(Inject.class)) {
                            new IllegalArgumentException("method cannot be annotated by @Unique and @Inject").printStackTrace(System.err);
                        }
                        if(method.isAnnotationPresent(Shadow.class)) shadowMethodsList.add(new ShadowMethod(method));
                        if(method.isAnnotationPresent(Unique.class)) tempUniqueMethods.add(method);
                        if(method.isAnnotationPresent(Inject.class)) {
                            List<Parameter> args = new ArrayList<>();
                            List<Parameter> locals = new ArrayList<>();

                            for(Parameter arg: method.getParameters()) {
                                if(arg.isAnnotationPresent(Arg.class) && arg.isAnnotationPresent(Local.class)) {
                                    new IllegalArgumentException("Inject parameter cannot be annotated by @Argument and @Local").printStackTrace(System.err);
                                }
                                if(arg.isAnnotationPresent(Arg.class)) args.add(arg);
                                if(arg.isAnnotationPresent(Local.class)) locals.add(arg);
                            }

                            tempArgumentParameters.put(method, args);
                            tempLocalParameters.put(method, locals);
                            tempInjectMethods.add(method);
                        }
                    }

                    if(clazz.isAnnotationPresent(Mixin.class)) {
                        Class<?> targetClass = clazz.getAnnotation(Mixin.class).value();
                        for (Method method : tempUniqueMethods) {
                            uniqueMethodsList.add(
                                    new UniqueMethod(
                                            method, targetClass,
                                            shadowFieldsList.toArray(new ShadowField[0]),
                                            shadowMethodsList.toArray(new ShadowMethod[0]),
                                            uniqueFieldsList.toArray(new UniqueField[0]),
                                            new UniqueMethod[0]
                                    )
                            );
                        }
                        for (UniqueMethod um : uniqueMethodsList) um.updateMethodMap(uniqueMethodsList.toArray(new UniqueMethod[0]));
                        for (Method method : tempInjectMethods) {
                            List<ArgumentParameter> args = new ArrayList<>();
                            List<LocalParameter> locals = new ArrayList<>();

                            if (tempArgumentParameters.containsKey(method)) {
                                for (Parameter parameter : tempArgumentParameters.get(method)) {
                                    args.add(new ArgumentParameter(parameter));
                                }
                            }

                            if (tempLocalParameters.containsKey(method)) {
                                for (Parameter parameter : tempLocalParameters.get(method)) {
                                    locals.add(new LocalParameter(parameter));
                                }
                            }

                            injectMethodsList.add(new InjectMethod(
                                    method,
                                    targetClass,
                                    shadowFieldsList.toArray(new ShadowField[0]),
                                    uniqueFieldsList.toArray(new UniqueField[0]),
                                    shadowMethodsList.toArray(new ShadowMethod[0]),
                                    uniqueMethodsList.toArray(new UniqueMethod[0]),
                                    args.toArray(new ArgumentParameter[0]),
                                    locals.toArray(new LocalParameter[0])
                            ));
                        }
                    }

                    MixinClass mixinClass = new MixinClass(
                            clazz,
                            shadowFieldsList.toArray(new ShadowField[0]),
                            uniqueFieldsList.toArray(new UniqueField[0]),
                            shadowMethodsList.toArray(new ShadowMethod[0]),
                            uniqueMethodsList.toArray(new UniqueMethod[0]),
                            injectMethodsList.toArray(new InjectMethod[0]),
                            interfaces
                    );

                    if(mixinClass.isValid()) mixins.add(mixinClass);

                    System.out.println(mixinClass.isValid());
                    System.out.println(Arrays.toString(mixinClass.shadowFields));
                    System.out.println(Arrays.toString(mixinClass.uniqueFields));
                    System.out.println(Arrays.toString(mixinClass.shadowMethods));
                    System.out.println(Arrays.toString(mixinClass.uniqueMethods));
                    System.out.println(Arrays.toString(mixinClass.injectMethods));

                } catch (ClassNotFoundException e) {
                    e.printStackTrace(System.err);
                }
            }
        }
        parser.close();
    }
}