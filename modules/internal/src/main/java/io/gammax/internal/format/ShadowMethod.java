package io.gammax.internal.format;

import io.gammax.api.Mixin;
import io.gammax.internal.util.DescriptorFormat;
import org.objectweb.asm.*;

import java.lang.reflect.Method;

public class ShadowMethod {
    private final Method method;
    private final String targetOwner;

    public ShadowMethod(Method method) {
        this.method = method;

        Class<?> targetClass = method.getDeclaringClass().getAnnotation(Mixin.class).target();

        this.targetOwner = targetClass.getName().replace('.', '/');
    }

    public Method getMethod() {
        return method;
    }

    public byte[] provideMethod(byte[] mixinBytes) {
        ClassReader reader = new ClassReader(mixinBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (name.equals(method.getName()) && desc.equals(DescriptorFormat.getMethodDescriptor(method)))
                            super.visitMethodInsn(opcode, targetOwner, method.getName(), DescriptorFormat.getMethodDescriptor(method), itf);
                        else super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                };
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}