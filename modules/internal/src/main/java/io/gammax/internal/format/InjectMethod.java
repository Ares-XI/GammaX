package io.gammax.internal.format;

import io.gammax.api.*;
import io.gammax.api.util.At;
import io.gammax.api.util.Signature;
import io.gammax.internal.MixinRegistry;
import io.gammax.internal.format.data.ArgumentParameter;
import io.gammax.internal.format.data.LocalParameter;
import io.gammax.internal.format.data.ShadowField;
import io.gammax.internal.format.data.ShadowMethod;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.TargetData;
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
            byte[] mixinBytes = MixinRegistry.getMixinClassLoader().getClassBytes(method.getDeclaringClass().getName());
            if (mixinBytes == null) return;

            ClassReader reader = new ClassReader(mixinBytes);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    Signature sig = annotation.signature();
                    String methodDesc = DescriptorFormat.getMethodDescriptor(sig.parameters(), sig.result());
                    if (name.equals(method.getName()) && desc.equals(methodDesc)) return visitor;
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public byte[] inject(byte[] targetClassBytes) {
        if (visitor.instructions == null || visitor.instructions.size() == 0) {
            return targetClassBytes;
        }

        ClassReader reader = new ClassReader(targetClassBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        boolean injected = false;
        String injectorName = "injector$" + UUID.randomUUID().toString().replace("-", "");

        MethodNode injectorMethod = createInjectorMethodNode(injectorName);
        classNode.methods.add(injectorMethod);

        for (MethodNode targetMethod : classNode.methods) {
            if (targetMethod.name.equals(annotation.method())) {
                injectIntoTargetMethod(targetMethod, injectorName);
                injected = true;
                break;
            }
        }

        if (!injected) {
            System.err.println("[Inject] Warning: Target method " + annotation.method() + " not found in " + targetClass.getName());
            return targetClassBytes;
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    public int getPriority() {
        return annotation.priority();
    }

    private MethodNode createInjectorMethodNode(String name) {
        Signature sig = annotation.signature();
        int access = DescriptorFormat.getMethodAccess(method) | Opcodes.ACC_PRIVATE;
        String desc = DescriptorFormat.getMethodDescriptor(sig.parameters(), sig.result());
        MethodNode mn = new MethodNode(access, name, desc, null, null);

        for (AbstractInsnNode node : visitor.instructions) mn.instructions.add(node);
        mn.maxLocals = visitor.maxLocals;
        mn.maxStack = visitor.maxStack;
        return mn;
    }

    private void injectIntoTargetMethod(MethodNode targetMethod, String injectorName) {
        AbstractInsnNode point = findInjectionPoint(targetMethod);
        if (point == null) return;

        InsnList callCode = new InsnList();
        boolean targetStatic = (targetMethod.access & Opcodes.ACC_STATIC) != 0;
        boolean injectorStatic = Modifier.isStatic(method.getModifiers());

        if (!targetStatic && !injectorStatic) callCode.add(new VarInsnNode(Opcodes.ALOAD, 0));

        for (ArgumentParameter ap : argumentParams) {
            Parameter param = ap.parameter();
            int argIndex = findParameterIndex(param);
            if (argIndex >= 0) {
                int loadOpcode = DescriptorFormat.getLoadOpcode(param.getType());
                int varIndex = getArgumentVarIndex(targetMethod, argIndex);
                callCode.add(new VarInsnNode(loadOpcode, varIndex));
            }
        }

        for (LocalParameter lp : localParams) {
            Parameter param = lp.parameter();
            int localIndex = lp.parameter().getAnnotation(Local.class).value();
            int foundIndex = findLocalVariableIndex(targetMethod, param.getName(), localIndex);
            if (foundIndex >= 0) {
                int loadOpcode = DescriptorFormat.getLoadOpcode(param.getType());
                callCode.add(new VarInsnNode(loadOpcode, foundIndex));
            }
        }

        Signature sig = annotation.signature();
        String desc = DescriptorFormat.getMethodDescriptor(sig.parameters(), sig.result());
        int invokeOpcode = injectorStatic ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
        callCode.add(new MethodInsnNode(
                invokeOpcode,
                targetClass.getName().replace('.', '/'),
                injectorName,
                desc,
                false
        ));

        switch (annotation.mode()) {
            case BEFORE:
                targetMethod.instructions.insertBefore(point, callCode);
                break;
            case AFTER:
                targetMethod.instructions.insert(point, callCode);
                break;
            case CANSEL:
                targetMethod.instructions.insertBefore(point, callCode);
                targetMethod.instructions.remove(point);
                break;
        }

        targetMethod.maxLocals = Math.max(targetMethod.maxLocals, visitor.maxLocals + 1);
        targetMethod.maxStack = Math.max(targetMethod.maxStack, visitor.maxStack + 2);
    }

    private AbstractInsnNode findInjectionPoint(MethodNode method) {
        if (annotation.at() == At.HEAD) return method.instructions.getFirst();

        if (annotation.at() == At.RETURN) {
            int found = 0;
            for (AbstractInsnNode insn : method.instructions) {
                int op = insn.getOpcode();
                if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                    if (found == annotation.index()) return insn;
                    found++;
                }
            }
            return null;
        }

        int found = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (matches(insn)) {
                if (found == annotation.index()) return insn;
                found++;
            }
        }
        return null;
    }

    private boolean matches(AbstractInsnNode insn) {
        int type = insn.getType();
        if (type == AbstractInsnNode.LABEL || type == AbstractInsnNode.LINE || type == AbstractInsnNode.FRAME) return false;

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

    private int findParameterIndex(Parameter param) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length; i++) if (params[i].equals(param)) return i;
        return -1;
    }

    private int getArgumentVarIndex(MethodNode method, int argIndex) {
        int base = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        Type[] argTypes = Type.getArgumentTypes(method.desc);
        for (int i = 0; i < argIndex; i++) base += argTypes[i].getSize();
        return base;
    }

    private int findLocalVariableIndex(MethodNode method, String name, int hint) {
        if (method.localVariables != null) {
            for (LocalVariableNode local : method.localVariables) {
                if (local.name.equals(name) && (hint == 0 || local.index == hint)) {
                    return local.index;
                }
            }
        }
        return hint >= 0 ? hint : -1;
    }
}