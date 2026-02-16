package io.gammax.internal.format;

import io.gammax.api.Inject;
import io.gammax.api.enums.InjectAt;
import io.gammax.api.enums.Mode;
import io.gammax.internal.MixinRegistry;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.visitor.InjectMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

public class InjectMethod {
    private final Method method;
    private final Class<?> targetClass;
    private final String targetMethodName;
    private final InjectAt injectAt;
    private final Mode mode;
    private final int index;
    private final InjectMethodVisitor visitor;

    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();

    public InjectMethod(Method method, Class<?> targetClass, ShadowField[] shadowFields, UniqueField[] uniqueFields, ShadowMethod[] shadowMethods, UniqueMethod[] uniqueMethods) {
        this.method = method;
        this.targetClass = targetClass;
        Inject annotation = method.getAnnotation(Inject.class);

        this.targetMethodName = annotation.method();
        this.injectAt = annotation.at().value();
        this.mode = annotation.at().mode();
        this.index = annotation.at().index();

        this.visitor = new InjectMethodVisitor(method, targetClass);

        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods, uniqueMethods);

        extractMethodInstructions();
    }

    private void buildFieldMap(ShadowField[] shadowFields, UniqueField[] uniqueFields) {
        String targetName = targetClass.getName().replace('.', '/');

        for (ShadowField sf : shadowFields) {
            String key = sf.getField().getName() + ":" + DescriptorFormat.getDescriptor(sf.getField().getType());
            fieldMap.put(key, targetName);
        }
        for (UniqueField uf : uniqueFields) {
            String key = uf.getField().getName() + ":" + DescriptorFormat.getDescriptor(uf.getField().getType());
            fieldMap.put(key, targetName);
        }
    }

    private void buildMethodMap(ShadowMethod[] shadowMethods, UniqueMethod[] uniqueMethods) {
        String targetName = targetClass.getName().replace('.', '/');

        for (ShadowMethod sm : shadowMethods) {
            String key = sm.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(sm.getMethod());
            methodMap.put(key, targetName);
        }
        for (UniqueMethod um : uniqueMethods) {
            String key = um.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(um.getMethod());
            methodMap.put(key, targetName);
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
                    if (name.equals(method.getName()) && desc.equals(DescriptorFormat.getMethodDescriptor(method))) return visitor;
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    private void injectIntoMethod(MethodNode targetMethod, AbstractInsnNode point) {
        InsnList injectCode = new InsnList();

        if (!Modifier.isStatic(method.getModifiers())) injectCode.add(new VarInsnNode(Opcodes.ALOAD, 0));

        Parameter[] parameters = method.getParameters();
        int localIndex = Modifier.isStatic(method.getModifiers()) ? 0 : 1;

        for (int i = 0; i < parameters.length; i++) {
            Class<?> paramType = parameters[i].getType();
            int loadOpcode = DescriptorFormat.getLoadOpcode(paramType);
            injectCode.add(new VarInsnNode(loadOpcode, DescriptorFormat.getParamIndex(targetMethod, i, localIndex)));
        }

        Map<LabelNode, LabelNode> labelMap = new HashMap<>();

        for (AbstractInsnNode insn : visitor.instructions) {
            if (insn instanceof FieldInsnNode fieldInsn) {
                String key = fieldInsn.name + ":" + fieldInsn.desc;
                String newOwner = fieldMap.get(key);
                if (newOwner != null) {
                    injectCode.add(new FieldInsnNode(fieldInsn.getOpcode(), newOwner, fieldInsn.name, fieldInsn.desc));
                    continue;
                }
            } else if (insn instanceof MethodInsnNode methodInsn) {
                String key = methodInsn.name + ":" + methodInsn.desc;
                String newOwner = methodMap.get(key);
                if (newOwner != null) {
                    injectCode.add(new MethodInsnNode(methodInsn.getOpcode(), newOwner, methodInsn.name, methodInsn.desc, methodInsn.itf));
                    continue;
                }
            }
            injectCode.add(insn.clone(labelMap));
        }

        switch (mode) {
            case BEFORE:
                targetMethod.instructions.insertBefore(point, injectCode);
                break;
            case AFTER:
                targetMethod.instructions.insert(point, injectCode);
                break;
            case CANSEL:
                targetMethod.instructions.insertBefore(point, injectCode);
                targetMethod.instructions.remove(point);
                break;
        }

        for (LineNumberNode line : visitor.lineNumbers) targetMethod.instructions.add(line);

        targetMethod.localVariables.addAll(visitor.localVariables);

        for (TryCatchBlockNode tcb : visitor.tryCatchBlocks) targetMethod.tryCatchBlocks.add(cloneTryCatch(tcb, targetMethod));

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, visitor.maxLocals + 1);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, visitor.maxStack + 2);
    }

    private AbstractInsnNode findInjectionPoint(MethodNode method) {
        int found = -1;

        for (AbstractInsnNode insn : method.instructions) {
            if (DescriptorFormat.matches(insn, injectAt)) {
                found++;
                if (found == index) {
                    return insn;
                }
            }
        }

        if (injectAt == InjectAt.HEAD) {
            return method.instructions.getFirst();
        }

        return null;
    }

    private TryCatchBlockNode cloneTryCatch(TryCatchBlockNode original, MethodNode method) {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();

        method.instructions.insertBefore(method.instructions.getFirst(), start);
        method.instructions.insert(method.instructions.getLast(), end);
        method.instructions.insert(method.instructions.getLast(), handler);

        return new TryCatchBlockNode(start, end, handler, original.type);
    }

    public byte[] inject(byte[] targetClassBytes) {
        if (visitor.instructions == null || visitor.instructions.size() == 0) return targetClassBytes;

        ClassReader reader = new ClassReader(targetClassBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean injected = false;

        for (MethodNode methodNode : classNode.methods) {
            if (methodNode.name.equals(targetMethodName)) {
                AbstractInsnNode point = findInjectionPoint(methodNode);
                if (point != null) {
                    injectIntoMethod(methodNode, point);
                    injected = true;
                }
            }
        }

        if (!injected) return targetClassBytes;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}