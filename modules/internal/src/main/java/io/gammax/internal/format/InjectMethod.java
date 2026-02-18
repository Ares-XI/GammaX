package io.gammax.internal.format;

import io.gammax.api.Inject;
import io.gammax.api.enums.Mode;
import io.gammax.api.enums.Instruction;
import io.gammax.internal.MixinRegistry;
import io.gammax.internal.util.DescriptorFormat;
import io.gammax.internal.util.visitor.InjectMethodVisitor;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class InjectMethod {
    private final Method method;
    private final Class<?> targetClass;
    private final String targetMethodName;
    private final Instruction instruction;
    private final Mode mode;
    private final int index;
    private final InjectMethodVisitor visitor;

    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();
    private final Class<?>[] parameters;
    private final Class<?> result;

    public InjectMethod(Method method, Class<?> targetClass, ShadowField[] shadowFields, UniqueField[] uniqueFields, ShadowMethod[] shadowMethods, UniqueMethod[] uniqueMethods) {
        this.method = method;
        this.targetClass = targetClass;
        Inject annotation = method.getAnnotation(Inject.class);

        this.targetMethodName = annotation.method();
        this.instruction = annotation.instruction();
        this.mode = annotation.mode();
        this.index = annotation.index();
        this.parameters = annotation.signature().parameters();
        this.result = annotation.signature().result();

        this.visitor = new InjectMethodVisitor(method, targetClass);

        visitor.fieldMap.putAll(this.fieldMap);
        visitor.methodMap.putAll(this.methodMap);

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
        visitor.fieldMap.putAll(fieldMap);
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
        visitor.methodMap.putAll(methodMap);
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

        int localIndex = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> paramType = parameters[i];
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
        int currentIndex = 0;

        for (AbstractInsnNode insn : method.instructions) {
            if (matches(insn)) {
                if (currentIndex == index) return insn;
                currentIndex++;
            }
        }

        if (instruction == Instruction.HEAD) return method.instructions.getFirst();

        if (instruction == Instruction.TAIL) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) return insn;
            }
        }

        return null;
    }

    private boolean matches(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();

        if (opcode == -1) return false;

        return switch (instruction) {
            case RETURN -> opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN;

            case INVOKE -> insn instanceof MethodInsnNode;
            case INVOKE_ASSIGN -> insn instanceof MethodInsnNode && isInvokeAssign(insn);
            case INVOKE_STRING -> insn instanceof MethodInsnNode && isInvokeString(insn);
            case INVOKE_DYNAMIC -> insn instanceof InvokeDynamicInsnNode;

            case NEW -> opcode == Opcodes.NEW;
            case NEW_ARRAY -> opcode == Opcodes.NEWARRAY;
            case ANEW_ARRAY -> opcode == Opcodes.ANEWARRAY;
            case MULTI_NEW_ARRAY -> opcode == Opcodes.MULTIANEWARRAY;

            case GET_FIELD -> opcode == Opcodes.GETFIELD;
            case PUT_FIELD -> opcode == Opcodes.PUTFIELD;
            case GET_STATIC -> opcode == Opcodes.GETSTATIC;
            case PUT_STATIC -> opcode == Opcodes.PUTSTATIC;

            case LOAD -> opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD;
            case STORE -> opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE;
            case IINC -> opcode == Opcodes.IINC;

            case JUMP -> opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE;
            case GOTO -> opcode == Opcodes.GOTO;
            case LOOKUP_SWITCH -> opcode == Opcodes.LOOKUPSWITCH;
            case TABLE_SWITCH -> opcode == Opcodes.TABLESWITCH;

            case THROW -> opcode == Opcodes.ATHROW;

            case CONSTANT -> insn instanceof LdcInsnNode;
            case CONSTANT_INT -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Integer;
            case CONSTANT_LONG -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Long;
            case CONSTANT_FLOAT -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Float;
            case CONSTANT_DOUBLE -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Double;
            case CONSTANT_STRING -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String;
            case CONSTANT_CLASS -> insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Type;

            case MONITOR_ENTER -> opcode == Opcodes.MONITORENTER;
            case MONITOR_EXIT -> opcode == Opcodes.MONITOREXIT;

            case CHECKCAST -> opcode == Opcodes.CHECKCAST;
            case INSTANCEOF -> opcode == Opcodes.INSTANCEOF;

            case ARRAY_LENGTH -> opcode == Opcodes.ARRAYLENGTH;
            case ARRAY_LOAD -> opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD;
            case ARRAY_STORE -> opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE;

            case ADD -> isArithmeticOp(opcode, Opcodes.IADD, Opcodes.LADD, Opcodes.FADD, Opcodes.DADD);
            case SUB -> isArithmeticOp(opcode, Opcodes.ISUB, Opcodes.LSUB, Opcodes.FSUB, Opcodes.DSUB);
            case MUL -> isArithmeticOp(opcode, Opcodes.IMUL, Opcodes.LMUL, Opcodes.FMUL, Opcodes.DMUL);
            case DIV -> isArithmeticOp(opcode, Opcodes.IDIV, Opcodes.LDIV, Opcodes.FDIV, Opcodes.DDIV);
            case REM -> isArithmeticOp(opcode, Opcodes.IREM, Opcodes.LREM, Opcodes.FREM, Opcodes.DREM);
            case NEG -> isArithmeticOp(opcode, Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG);

            case SHL -> isArithmeticOp(opcode, Opcodes.ISHL, Opcodes.LSHL);
            case SHR -> isArithmeticOp(opcode, Opcodes.ISHR, Opcodes.LSHR);
            case USHR -> isArithmeticOp(opcode, Opcodes.IUSHR, Opcodes.LUSHR);
            case AND -> isArithmeticOp(opcode, Opcodes.IAND, Opcodes.LAND);
            case OR -> isArithmeticOp(opcode, Opcodes.IOR, Opcodes.LOR);
            case XOR -> isArithmeticOp(opcode, Opcodes.IXOR, Opcodes.LXOR);

            case LCMP -> opcode == Opcodes.LCMP;
            case FCMPL -> opcode == Opcodes.FCMPL;
            case FCMPG -> opcode == Opcodes.FCMPG;
            case DCMPL -> opcode == Opcodes.DCMPL;
            case DCMPG -> opcode == Opcodes.DCMPG;

            case I2L -> opcode == Opcodes.I2L;
            case I2F -> opcode == Opcodes.I2F;
            case I2D -> opcode == Opcodes.I2D;
            case L2I -> opcode == Opcodes.L2I;
            case L2F -> opcode == Opcodes.L2F;
            case L2D -> opcode == Opcodes.L2D;
            case F2I -> opcode == Opcodes.F2I;
            case F2L -> opcode == Opcodes.F2L;
            case F2D -> opcode == Opcodes.F2D;
            case D2I -> opcode == Opcodes.D2I;
            case D2L -> opcode == Opcodes.D2L;
            case D2F -> opcode == Opcodes.D2F;
            case I2B -> opcode == Opcodes.I2B;
            case I2C -> opcode == Opcodes.I2C;
            case I2S -> opcode == Opcodes.I2S;
            case EARLY_RETURN -> opcode == Opcodes.RETURN && isEarlyReturn(insn);

            default -> false;
        };
    }

    private boolean isArithmeticOp(int opcode, int... validOpcodes) {
        for (int valid : validOpcodes) if (opcode == valid) return true;
        return false;
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

        if (prev.getOpcode() == Opcodes.LDC && prev instanceof LdcInsnNode) {
            Object cst = ((LdcInsnNode) prev).cst;
            return cst instanceof String;
        }
        return false;
    }

    private boolean isEarlyReturn(AbstractInsnNode insn) {
        return insn.getNext() != null && insn.getNext().getOpcode() != -1;
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