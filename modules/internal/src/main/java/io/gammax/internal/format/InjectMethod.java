package io.gammax.internal.format;

import io.gammax.api.Argument;
import io.gammax.api.Inject;
import io.gammax.api.util.Mode;
import io.gammax.api.util.TargetReference;
import io.gammax.api.util.At;
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

    private final ArgumentParameter[] argumentParameters;
    private final LocalParameter[] localParameters = null;

    private final String targetMethodName;
    private final At at;
    private final TargetReference target;
    private final Mode mode;
    private final int index;
    private final InjectMethodVisitor visitor;
    private final TargetData targetData;

    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final Class<?>[] parameters;
    private final Class<?> result;

    public InjectMethod(Method method, Class<?> targetClass, ShadowField[] shadowFields, UniqueField[] uniqueFields, ShadowMethod[] shadowMethods, UniqueMethod[] uniqueMethods, ArgumentParameter[] argumentParameters) {
        this.method = method;
        this.targetClass = targetClass;

        this.argumentParameters = argumentParameters;
//        this.localParameters = localParameters;

        Inject annotation = method.getAnnotation(Inject.class);
        this.targetMethodName = annotation.method();
        this.at = annotation.at();
        this.target = annotation.reference();
        this.mode = annotation.mode();
        this.index = annotation.index();
        this.parameters = annotation.signature().parameters();
        this.result = annotation.signature().result();

        this.targetData = new TargetData(target, at);
        this.visitor = new InjectMethodVisitor(method, targetClass);

        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods, uniqueMethods);

        if (mode == Mode.CANSEL) validateCancellableReturnType();

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
        visitor.fieldMap.putAll(fieldMap);
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
        visitor.methodMap.putAll(methodMap);
    }

    private void validateCancellableReturnType() {
        Class<?> requiredType = getRequiredReturnTypeForCancellable();
        if (method.getReturnType() != requiredType) {
            new IllegalArgumentException("CANSEL injector for " + at + " must return " + requiredType.getName() + ", but returns " + method.getReturnType().getName()
            ).printStackTrace(System.err);
        }
    }

    private Class<?> getRequiredReturnTypeForCancellable() {
        return switch (at) {
            case HEAD, TAIL, JUMP, GOTO, LOAD, STORE, MONITOR_ENTER, MONITOR_EXIT, THROW, ARRAY_STORE -> void.class;
            case RETURN -> result;
            case INVOKE, INVOKE_ASSIGN, INVOKE_STRING, INVOKE_DYNAMIC, FIELD -> target.signature().result();
            case NEW, NEW_ARRAY, ANEW_ARRAY, MULTI_NEW_ARRAY, CHECKCAST, INSTANCEOF -> target.owner() != void.class ? target.owner() : Object.class;
            case CONSTANT, ARRAY_LOAD -> target.signature().result() != void.class ? target.signature().result() : Object.class;
            case ARRAY_LENGTH -> int.class;
        };
    }

    private void extractMethodInstructions() {
        try {
            byte[] mixinBytes = MixinRegistry.getMixinClassLoader().getClassBytes(method.getDeclaringClass().getName());
            if (mixinBytes == null) return;

            ClassReader reader = new ClassReader(mixinBytes);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    String methodDesc = DescriptorFormat.getMethodDescriptor(parameters, result);
                    if (name.equals(method.getName()) && desc.equals(methodDesc)) return visitor;
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

        Type[] targetArgTypes = Type.getArgumentTypes(targetMethod.desc);

        for (ArgumentParameter argParam : argumentParameters) {
            Parameter param = argParam.parameter();
            int argIndex = param.getAnnotation(Argument.class).value();
            Class<?> paramType = param.getType();

            if (argIndex >= 0 && argIndex < targetArgTypes.length) {
                int loadOpcode = DescriptorFormat.getLoadOpcode(paramType);
                int varIndex = getArgumentVarIndex(targetMethod, argIndex);
                injectCode.add(new VarInsnNode(loadOpcode, varIndex));
            } else {
                System.err.println("[Inject] Warning: @Argument index " + argIndex + " out of bounds for method " + targetMethod.name);
                injectCode.add(getDefaultValueInsn(paramType));
            }
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

        if (mode == Mode.CANSEL && requiresReturnValue(at)) {
            AbstractInsnNode last = injectCode.getLast();
            int expectedReturnOpcode = getReturnOpcodeForType(getRequiredReturnTypeForCancellable());
            if (last == null || last.getOpcode() != expectedReturnOpcode) {
                new IllegalStateException(
                        "CANSEL injector for " + at + " must end with " +
                                opcodeToString(expectedReturnOpcode) + " instruction"
                ).printStackTrace(System.err);
            }
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

    private boolean requiresReturnValue(At at) {
        return switch (at) {
            case HEAD, TAIL, JUMP, GOTO, LOAD, STORE, MONITOR_ENTER, MONITOR_EXIT, THROW, ARRAY_STORE ->
                    false;
            case RETURN, INVOKE, INVOKE_ASSIGN, INVOKE_STRING, INVOKE_DYNAMIC,
                 FIELD, NEW, NEW_ARRAY, ANEW_ARRAY, MULTI_NEW_ARRAY, CHECKCAST, INSTANCEOF,
                 CONSTANT, ARRAY_LENGTH, ARRAY_LOAD ->
                    true;
        };
    }

    private int getReturnOpcodeForType(Class<?> type) {
        if (type == void.class) return -1;
        if (type == int.class || type == boolean.class || type == byte.class || type == char.class || type == short.class) return Opcodes.IRETURN;
        if (type == long.class) return Opcodes.LRETURN;
        if (type == float.class) return Opcodes.FRETURN;
        if (type == double.class) return Opcodes.DRETURN;
        return Opcodes.ARETURN;
    }

    private String opcodeToString(int opcode) {
        return switch (opcode) {
            case Opcodes.IRETURN -> "IRETURN";
            case Opcodes.LRETURN -> "LRETURN";
            case Opcodes.FRETURN -> "FRETURN";
            case Opcodes.DRETURN -> "DRETURN";
            case Opcodes.ARETURN -> "ARETURN";
            case Opcodes.RETURN -> "RETURN";
            default -> "UNKNOWN";
        };
    }

    private AbstractInsnNode findInjectionPoint(MethodNode method) {
        if (at == At.HEAD) return method.instructions.getFirst();

        if (at == At.TAIL) {
            int found = 0;
            AbstractInsnNode lastReturn = null;

            for (AbstractInsnNode insn : method.instructions) {
                if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) {
                    if (found == index) return insn;
                    lastReturn = insn;
                    found++;
                }
            }

            if (found > 0) {
                System.err.println("[Inject] Warning: Index " + index + " too large for TAIL, using last RETURN");
                return lastReturn;
            }

            return null;
        }

        int found = 0;
        AbstractInsnNode lastMatch = null;

        for (AbstractInsnNode insn : method.instructions) {
            if (matches(insn)) {
                if (found == index) return insn;
                lastMatch = insn;
                found++;
            }
        }

        if (found == 0) {
            System.err.println("[Inject] Warning: No matching instruction found for " + at + " in " + targetMethodName);
            return null;
        }

        if (index >= found) {
            System.err.println("[Inject] Warning: Index " + index + " too large for " + at + " in " + targetMethodName + " (max: " + (found-1) + "), using last match");
            return lastMatch;
        }

        return null;
    }

    private boolean matches(AbstractInsnNode insn) {
        if (insn.getType() == AbstractInsnNode.LABEL || insn.getType() == AbstractInsnNode.LINE || insn.getType() == AbstractInsnNode.FRAME) return false;

        int opcode = insn.getOpcode();
        boolean typeMatches = switch (at) {
            case HEAD -> false;
            case RETURN -> opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;
            case TAIL -> false;

            case INVOKE, INVOKE_ASSIGN, INVOKE_STRING, INVOKE_DYNAMIC ->
                    insn instanceof MethodInsnNode;
            case NEW, NEW_ARRAY, ANEW_ARRAY, MULTI_NEW_ARRAY, CHECKCAST, INSTANCEOF ->
                    insn instanceof TypeInsnNode;
            case FIELD ->
                    insn instanceof FieldInsnNode;
            case ARRAY_LENGTH ->
                    opcode == Opcodes.ARRAYLENGTH;
            case ARRAY_LOAD ->
                    opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD;
            case ARRAY_STORE ->
                    opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE;
            case LOAD ->
                    opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
            case STORE ->
                    opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
            case JUMP ->
                    opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE;
            case GOTO ->
                    opcode == Opcodes.GOTO;
            case CONSTANT ->
                    insn instanceof LdcInsnNode;
            case MONITOR_ENTER ->
                    opcode == Opcodes.MONITORENTER;
            case MONITOR_EXIT ->
                    opcode == Opcodes.MONITOREXIT;
            case THROW ->
                    opcode == Opcodes.ATHROW;
        };

        if (!typeMatches) return false;
        if (at == At.INVOKE_ASSIGN && !isInvokeAssign(insn)) return false;
        if (at == At.INVOKE_STRING && !isInvokeString(insn)) return false;
        return switch (at) {
            case INVOKE, INVOKE_ASSIGN, INVOKE_STRING, INVOKE_DYNAMIC,
                 FIELD, NEW, CHECKCAST, INSTANCEOF, LOAD, STORE ->
                    targetData.matches(insn);
            default -> true;
        };
    }

    private int getArgumentVarIndex(MethodNode method, int argIndex) {
        int baseIndex = Modifier.isStatic(method.access) ? 0 : 1;
        Type[] argTypes = Type.getArgumentTypes(method.desc);
        int index = baseIndex;

        for (int i = 0; i < argIndex; i++) {
            index += argTypes[i].getSize();
        }
        return index;
    }

    private AbstractInsnNode getDefaultValueInsn(Class<?> type) {
        if (type == int.class || type == boolean.class || type == byte.class ||
                type == char.class || type == short.class) {
            return new InsnNode(Opcodes.ICONST_0);
        }
        if (type == long.class) return new InsnNode(Opcodes.LCONST_0);
        if (type == float.class) return new InsnNode(Opcodes.FCONST_0);
        if (type == double.class) return new InsnNode(Opcodes.DCONST_0);
        return new InsnNode(Opcodes.ACONST_NULL);
    }

    private boolean isInvokeAssign(AbstractInsnNode insn) {
        AbstractInsnNode next = insn.getNext();
        if (next == null) return false;
        int nextOp = next.getOpcode();
        return nextOp >= Opcodes.ISTORE && nextOp <= Opcodes.ASTORE;
    }

    private boolean isInvokeString(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode)) return false;
        AbstractInsnNode prev = insn.getPrevious();
        if (prev == null) return false;
        return prev.getOpcode() == Opcodes.LDC && prev instanceof LdcInsnNode && ((LdcInsnNode) prev).cst instanceof String;
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

        if (!injected) {
            System.err.println("[Inject] Warning: Target method " + targetMethodName + " not found in " + targetClass.getName());
            return targetClassBytes;
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }
}