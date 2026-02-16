package io.gammax.internal.format;

import io.gammax.api.Mixin;
import io.gammax.internal.util.DescriptorFormat;
import org.objectweb.asm.*;

import java.lang.reflect.Field;

public class ShadowField {
    private final Field field;
    private final String targetOwner;

    public ShadowField(Field field) {
        this.field = field;
        Class<?> targetClass = field.getDeclaringClass().getAnnotation(Mixin.class).target();
        this.targetOwner = targetClass.getName().replace('.', '/');
    }

    public Field getField() {
        return field;
    }

    public byte[] provideField(byte[] mixinBytes) {
        ClassReader reader = new ClassReader(mixinBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

                if ((access & Opcodes.ACC_ABSTRACT) != 0) return mv;

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        if (name.equals(field.getName()) && desc.equals(DescriptorFormat.getDescriptor(field.getType())))
                            mv.visitFieldInsn(
                                    opcode,
                                    targetOwner,
                                    field.getName(),
                                    DescriptorFormat.getDescriptor(field.getType())
                            );
                        else super.visitFieldInsn(opcode, owner, name, desc);
                    }
                };
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}