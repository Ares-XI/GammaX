package io.gammax.internal.format.functional;

import io.gammax.api.*;
import io.gammax.api.util.At;
import io.gammax.api.util.Signature;
import io.gammax.internal.format.FunctionalModifier;
import io.gammax.internal.format.data.ArgumentParameter;
import io.gammax.internal.format.data.LocalParameter;
import io.gammax.internal.format.data.ShadowField;
import io.gammax.internal.format.data.ShadowMethod;
import io.gammax.internal.instrumentation.cashing.MixinClassLoader;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.data.TargetData;
import io.gammax.internal.util.visitor.InjectMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class InjectMethod implements FunctionalModifier {
    private final Method method;
    private final Class<?> targetClass;
    private final Inject annotation;
    private final TargetData targetData;
    private final InjectMethodVisitor visitor;
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final ArgumentParameter[] argumentParams;
    private final LocalParameter[] localParams;

    public InjectMethod(Method method, Class<?> targetClass, ShadowField[] shadowFields, UniqueField[] uniqueFields, ShadowMethod[] shadowMethods, UniqueMethod[] uniqueMethods, ArgumentParameter[] argumentParams, LocalParameter[] localParams) {
        this.method = method;
        this.targetClass = targetClass;
        this.annotation = method.getAnnotation(Inject.class);
        this.argumentParams = argumentParams;
        this.localParams = localParams;

        this.targetData = new TargetData(annotation.reference(), annotation.at());
        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods, uniqueMethods);

        this.visitor = new InjectMethodVisitor(method, targetClass, fieldMap, methodMap);
        extractMethodInstructions();
    }

    private void buildFieldMap(ShadowField[] shadowFields, UniqueField[] uniqueFields) {
        String targetName = targetClass.getName().replace('.', '/');
        for (ShadowField sf : shadowFields) {
            String key = sf.field().getName() + ":" + DescriptorFormat.getDescriptor(sf.field().getType());
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
            String key = sm.method().getName() + ":" + DescriptorFormat.getMethodDescriptor(sm.method());
            methodMap.put(key, targetName);
        }
        for (UniqueMethod um : uniqueMethods) {
            String key = um.getMethod().getName() + ":" + DescriptorFormat.getMethodDescriptor(um.getMethod());
            methodMap.put(key, targetName);
        }
    }

    private void extractMethodInstructions() {
        try {
            byte[] mixinBytes = MixinClassLoader.instance.getClassBytes(method.getDeclaringClass().getName());
            if (mixinBytes == null) {
                System.out.println("[Inject] mixinBytes = null for " + method.getName());
                return;
            }

            String injectorDesc = DescriptorFormat.getMethodDescriptor(method);
            new ClassReader(mixinBytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (name.equals(method.getName()) && desc.equals(injectorDesc)) return visitor;
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (visitor.instructions == null) {
                System.out.println("[Inject] ❌ instructions is null for " + method.getName());
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Override
    public byte[] modify(byte[] targetClassBytes) {
        if (visitor.instructions == null || visitor.instructions.size() == 0) {
            System.out.println("[Inject] instructions == null for " + method.getName());
            return targetClassBytes;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(targetClassBytes).accept(classNode, ClassReader.EXPAND_FRAMES);

        String injectorName = "injector$" + UUID.randomUUID().toString().replace("-", "");
        MethodNode injectorMethod = createInjectorMethodNode(injectorName);
        classNode.methods.add(injectorMethod);

        Signature targetSig = annotation.signature();
        String targetDesc = DescriptorFormat.getMethodDescriptor(targetSig.parameters(), targetSig.result());
        boolean injected = false;

        for (MethodNode targetMethod : classNode.methods) {
            if (targetMethod.name.equals(annotation.method()) && targetMethod.desc.equals(targetDesc)) {
                injectIntoTargetMethod(targetMethod, injectorName);
                injected = true;
                break;
            }
        }

        if (!injected) System.err.println("[Inject] Warning: Target method " + annotation.method() + targetDesc + " not found in " + targetClass.getName());

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private MethodNode createInjectorMethodNode(String name) {
        int access = Opcodes.ACC_PRIVATE;
        if (Modifier.isStatic(method.getModifiers())) access |= Opcodes.ACC_STATIC;

        MethodNode mn = new MethodNode(access, name, DescriptorFormat.getMethodDescriptor(method), null, null);
        visitor.instructions.forEach(mn.instructions::add);
        mn.maxLocals = visitor.maxLocals;
        mn.maxStack = visitor.maxStack;
        return mn;
    }

    private void injectIntoTargetMethod(MethodNode targetMethod, String injectorName) {
        AbstractInsnNode point = findInjectionPoint(targetMethod);
        if (point == null) return;

        if (targetMethod.name.equals("<init>") && annotation.at() == At.HEAD) {
            for (AbstractInsnNode insn : targetMethod.instructions) {
                if (insn instanceof MethodInsnNode min && min.name.equals("<init>") && min.getOpcode() == Opcodes.INVOKESPECIAL) {
                    point = insn.getNext();
                    break;
                }
            }
        }

        InsnList callCode = new InsnList();
        boolean targetStatic = (targetMethod.access & Opcodes.ACC_STATIC) != 0;
        boolean injectorStatic = Modifier.isStatic(method.getModifiers());

        if (!targetStatic && !injectorStatic) callCode.add(new VarInsnNode(Opcodes.ALOAD, 0));

        for (ArgumentParameter ap : argumentParams) {
            int idx = ap.parameter().getAnnotation(Arg.class).value();
            int base = targetStatic ? 0 : 1;
            Type[] types = Type.getArgumentTypes(targetMethod.desc);
            for (int i = 0; i < idx; i++) base += types[i].getSize();
            callCode.add(new VarInsnNode(DescriptorFormat.getLoadOpcode(ap.parameter().getType()), base));
        }

        for (LocalParameter lp : localParams) {
            int idx = lp.parameter().getAnnotation(Local.class).value();
            callCode.add(new VarInsnNode(DescriptorFormat.getLoadOpcode(lp.parameter().getType()), idx));
        }

        callCode.add(new MethodInsnNode(injectorStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                targetClass.getName().replace('.', '/'), injectorName,
                DescriptorFormat.getMethodDescriptor(method), false));

        switch (annotation.mode()) {
            case BEFORE -> targetMethod.instructions.insertBefore(point, callCode);
            case AFTER -> targetMethod.instructions.insert(point, callCode);
            case CANSEL -> {
                targetMethod.instructions.insertBefore(point, callCode);
                targetMethod.instructions.remove(point);
            }
        }

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, visitor.maxLocals + 1);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, visitor.maxStack + 2);
    }

    private AbstractInsnNode findInjectionPoint(MethodNode method) {
        if (annotation.at() == At.HEAD) return method.instructions.getFirst();
        if (annotation.at() == At.RETURN) {
            int f = 0;
            for (AbstractInsnNode insn : method.instructions) {
                int op = insn.getOpcode();
                if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN && f++ == annotation.index()) return insn;
            }
            return null;
        }
        int f = 0;
        for (AbstractInsnNode insn : method.instructions) {
            int t = insn.getType();
            if (t == AbstractInsnNode.LABEL || t == AbstractInsnNode.LINE || t == AbstractInsnNode.FRAME) continue;
            if (matches(insn) && f++ == annotation.index()) return insn;
        }
        return null;
    }

    private boolean matches(AbstractInsnNode insn) {
        return switch (annotation.at()) {
            case INVOKE -> insn instanceof MethodInsnNode && targetData.matches(insn);
            case FIELD -> insn instanceof FieldInsnNode && targetData.matches(insn);
            case NEW -> insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.NEW && targetData.matches(insn);
            case CONSTANT -> insn instanceof LdcInsnNode;
            case THROW -> insn.getOpcode() == Opcodes.ATHROW;
            case CHECKCAST -> insn.getOpcode() == Opcodes.CHECKCAST && targetData.matches(insn);
            case INSTANCEOF -> insn.getOpcode() == Opcodes.INSTANCEOF && targetData.matches(insn);
            case ARRAY_LENGTH -> insn.getOpcode() == Opcodes.ARRAYLENGTH;
            default -> false;
        };
    }

    public int getPriority() {
        return annotation.priority();
    }
}