package io.gammax.internal.util;

import org.objectweb.asm.Opcodes;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class DescriptorFormat {

    public static int getAccessModifiers(Field field) {
        int modifiers = field.getModifiers();
        int access = 0;

        if (Modifier.isPublic(modifiers)) access |= Opcodes.ACC_PUBLIC;
        if (Modifier.isPrivate(modifiers)) access |= Opcodes.ACC_PRIVATE;
        if (Modifier.isProtected(modifiers)) access |= Opcodes.ACC_PROTECTED;
        if (Modifier.isStatic(modifiers)) access |= Opcodes.ACC_STATIC;
        if (Modifier.isFinal(modifiers)) access |= Opcodes.ACC_FINAL;
        if (Modifier.isVolatile(modifiers)) access |= Opcodes.ACC_VOLATILE;
        if (Modifier.isTransient(modifiers)) access |= Opcodes.ACC_TRANSIENT;

        return access;
    }

    public static int getMethodAccess(Method method) {
        int modifiers = method.getModifiers();
        int access = 0;

        if (Modifier.isPublic(modifiers)) access |= Opcodes.ACC_PUBLIC;
        if (Modifier.isPrivate(modifiers)) access |= Opcodes.ACC_PRIVATE;
        if (Modifier.isProtected(modifiers)) access |= Opcodes.ACC_PROTECTED;
        if (Modifier.isStatic(modifiers)) access |= Opcodes.ACC_STATIC;
        if (Modifier.isFinal(modifiers)) access |= Opcodes.ACC_FINAL;
        if (Modifier.isSynchronized(modifiers)) access |= Opcodes.ACC_SYNCHRONIZED;
        if (Modifier.isNative(modifiers)) access |= Opcodes.ACC_NATIVE;
        if (Modifier.isAbstract(modifiers)) access |= Opcodes.ACC_ABSTRACT;
        if (Modifier.isStrict(modifiers)) access |= Opcodes.ACC_STRICT;

        return access;
    }

    public static String getMethodDescriptor(Method method) {
        StringBuilder desc = new StringBuilder("(");
        for (Class<?> param : method.getParameterTypes()) desc.append(getDescriptor(param));
        desc.append(")").append(getDescriptor(method.getReturnType()));

        return desc.toString();
    }

    public static String getDescriptor(Class<?> type) {
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        if (type == void.class) return "V";

        if (type.isArray()) return "[" + getDescriptor(type.getComponentType());
        return "L" + type.getName().replace('.', '/') + ";";
    }

    private DescriptorFormat() {}
}