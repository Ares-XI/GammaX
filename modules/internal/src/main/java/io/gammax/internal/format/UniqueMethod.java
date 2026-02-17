package io.gammax.internal.format;

import io.gammax.internal.MixinRegistry;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.visitor.UniqueMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;

public class UniqueMethod {
    private final Method method;
    private final Class<?> targetClass;

    private InsnList instructions;
    public UniqueMethodVisitor visitor;

    public UniqueMethod(Method method, Class<?> targetClass, ShadowField[] shadowFields, ShadowMethod[] shadowMethods, UniqueField[] uniqueFields, UniqueMethod[] uniqueMethods) {
        this.method = method;
        this.targetClass = targetClass;
        this.visitor = new UniqueMethodVisitor();

        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods, uniqueMethods);
        extractMethodInstructions();
    }

    public Method getMethod() {
        return method;
    }

    public void updateMethodMap(UniqueMethod[] allUniqueMethods) {
        String targetName = targetClass.getName().replace('.', '/');

        for (UniqueMethod um : allUniqueMethods) {
            String key = um.getMethod().getName() + ":" +
                    DescriptorFormat.getMethodDescriptor(um.getMethod());
            visitor.methodMap.put(key, targetName);
        }
    }

    private void buildFieldMap(ShadowField[] shadowFields, UniqueField[] uniqueFields) {
        for (ShadowField sf : shadowFields) {
            String key = sf.getField().getName() + ":" + DescriptorFormat.getDescriptor(sf.getField().getType());
            visitor.fieldMap.put(key, targetClass.getName().replace('.', '/'));
        }
        for (UniqueField uf : uniqueFields) {
            String key = uf.getField().getName() + ":" + DescriptorFormat.getDescriptor(uf.getField().getType());
            visitor.fieldMap.put(key, targetClass.getName().replace('.', '/'));
        }
    }

    private void buildMethodMap(ShadowMethod[] shadowMethods, UniqueMethod[] uniqueMethods) {
        for (ShadowMethod sm : shadowMethods) {
            String key = sm.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(sm.getMethod());
            visitor.methodMap.put(key, targetClass.getName().replace('.', '/'));
        }
        for (UniqueMethod um: uniqueMethods) {
            String key = um.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(um.getMethod());
            visitor.methodMap.put(key, targetClass.getName().replace('.', '/'));
        }
    }

    private void extractMethodInstructions() {
        try {
            byte[] mixinBytes = MixinRegistry.getMixinClassLoader().getClassBytes(method.getDeclaringClass().getName());
            if (mixinBytes == null) return;

            ClassReader reader = new ClassReader(mixinBytes);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (name.equals(UniqueMethod.this.method.getName()) && desc.equals(DescriptorFormat.getMethodDescriptor(method))) {
                        return visitor;
                    }
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            instructions = visitor.instructions;

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public byte[] addMethod(byte[] targetClassBytes) {
        if (instructions == null || instructions.size() == 0) return targetClassBytes;

        ClassReader reader = new ClassReader(targetClassBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor vis = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = cv.visitMethod(DescriptorFormat.getMethodAccess(method), method.getName(), DescriptorFormat.getMethodDescriptor(method), null, null);

                for (TryCatchBlockNode tcb : visitor.tryCatchBlocks) tcb.accept(mv);

                mv.visitCode();

                for (AbstractInsnNode insn : visitor.instructions) insn.accept(mv);
                for (LineNumberNode line : visitor.lineNumbers) line.accept(mv);
                for (LocalVariableNode local : visitor.localVariables) local.accept(mv);

                mv.visitMaxs(0, 0);
                mv.visitEnd();

                super.visitEnd();
            }
        };

        reader.accept(vis, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}