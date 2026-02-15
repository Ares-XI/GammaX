package io.gammax.internal.format;

import io.gammax.internal.MixinRegistry;
import io.gammax.internal.util.DescriptorFormat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.*;

public class UniqueMethod {
    public final Method method;
    private final Class<?> targetClass;

    private InsnList instructions;
    private final List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
    private final List<LocalVariableNode> localVariables = new ArrayList<>();
    private final List<LineNumberNode> lineNumbers = new ArrayList<>();
    private final Map<String, String> fieldMap = new HashMap<>();
    private final Map<String, String> methodMap = new HashMap<>();

    public UniqueMethod(Method method, Class<?> targetClass, ShadowField[] shadowFields, ShadowMethod[] shadowMethods, UniqueField[] uniqueFields) {
        this.method = method;
        this.targetClass = targetClass;

        buildFieldMap(shadowFields, uniqueFields);
        buildMethodMap(shadowMethods);
        extractMethodInstructions();
    }

    private void buildFieldMap(ShadowField[] shadowFields, UniqueField[] uniqueFields) {
        for (ShadowField sf : shadowFields) {
            String key = sf.field.getName() + ":" + DescriptorFormat.getDescriptor(sf.field.getType());
            fieldMap.put(key, targetClass.getName().replace('.', '/'));
        }
        for (UniqueField uf : uniqueFields) {
            String key = uf.field.getName() + ":" + DescriptorFormat.getDescriptor(uf.field.getType());
            fieldMap.put(key, targetClass.getName().replace('.', '/'));
        }
    }

    private void buildMethodMap(ShadowMethod[] shadowMethods) {
        for (ShadowMethod sm : shadowMethods) {
            String key = sm.method.getName() + ":" + DescriptorFormat.getMethodDescriptor(sm.method);
            methodMap.put(key, targetClass.getName().replace('.', '/'));
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
                        return new MethodVisitor(Opcodes.ASM9) {
                            private final InsnList insnList = new InsnList();

                            @Override
                            public void visitCode() {}

                            @Override
                            public void visitInsn(int opcode) {
                                insnList.add(new InsnNode(opcode));
                            }

                            @Override
                            public void visitIntInsn(int opcode, int operand) {
                                insnList.add(new IntInsnNode(opcode, operand));
                            }

                            @Override
                            public void visitVarInsn(int opcode, int var) {
                                insnList.add(new VarInsnNode(opcode, var));
                            }

                            @Override
                            public void visitTypeInsn(int opcode, String type) {
                                insnList.add(new TypeInsnNode(opcode, type));
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                                String key = name + ":" + desc;
                                String newOwner = fieldMap.get(key);

                                if (newOwner != null) insnList.add(new FieldInsnNode(opcode, newOwner, name, desc));
                                else insnList.add(new FieldInsnNode(opcode, owner, name, desc));
                            }

                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                                String key = name + ":" + desc;
                                String newOwner = methodMap.get(key);

                                if (newOwner != null) insnList.add(new MethodInsnNode(opcode, newOwner, name, desc, itf));
                                else insnList.add(new MethodInsnNode(opcode, owner, name, desc, itf));
                            }

                            @Override
                            public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
                                insnList.add(new InvokeDynamicInsnNode(name, desc, bsm, bsmArgs));
                            }

                            @Override
                            public void visitJumpInsn(int opcode, Label label) {
                                insnList.add(new JumpInsnNode(opcode, new LabelNode(label)));
                            }

                            @Override
                            public void visitLabel(Label label) {
                                insnList.add(new LabelNode(label));
                            }

                            @Override
                            public void visitLdcInsn(Object value) {
                                insnList.add(new LdcInsnNode(value));
                            }

                            @Override
                            public void visitIincInsn(int var, int increment) {
                                insnList.add(new IincInsnNode(var, increment));
                            }

                            @Override
                            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                                LabelNode dfltNode = new LabelNode(dflt);
                                LabelNode[] labelNodes = new LabelNode[labels.length];
                                for (int i = 0; i < labels.length; i++) labelNodes[i] = new LabelNode(labels[i]);
                                insnList.add(new TableSwitchInsnNode(min, max, dfltNode, labelNodes));
                            }

                            @Override
                            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                                LabelNode dfltNode = new LabelNode(dflt);
                                LabelNode[] labelNodes = new LabelNode[labels.length];
                                for (int i = 0; i < labels.length; i++) labelNodes[i] = new LabelNode(labels[i]);
                                insnList.add(new LookupSwitchInsnNode(dfltNode, keys, labelNodes));
                            }

                            @Override
                            public void visitMultiANewArrayInsn(String desc, int dims) {
                                insnList.add(new MultiANewArrayInsnNode(desc, dims));
                            }

                            @Override
                            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                                tryCatchBlocks.add(new TryCatchBlockNode(
                                        new LabelNode(start),
                                        new LabelNode(end),
                                        new LabelNode(handler),
                                        type
                                ));
                            }

                            @Override
                            public void visitLineNumber(int line, Label start) {
                                lineNumbers.add(new LineNumberNode(line, new LabelNode(start)));
                            }

                            @Override
                            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                                localVariables.add(new LocalVariableNode(
                                        name, desc, signature,
                                        new LabelNode(start),
                                        new LabelNode(end),
                                        index
                                ));
                            }

                            @Override
                            public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                                List<Object> localList = new ArrayList<>(Arrays.asList(local));
                                List<Object> stackList = new ArrayList<>(Arrays.asList(stack));
                                insnList.add(new FrameNode(type, nLocal, localList.toArray(), nStack, stackList.toArray()));
                            }

                            @Override
                            public void visitMaxs(int maxStack, int maxLocals) {}

                            @Override
                            public void visitEnd() {
                                instructions = insnList;
                            }
                        };
                    }
                    return null;
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public byte[] addMethod(byte[] targetClassBytes) {
        if (instructions == null || instructions.size() == 0) return targetClassBytes;

        ClassReader reader = new ClassReader(targetClassBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitEnd() {
                MethodVisitor mv = cv.visitMethod(DescriptorFormat.getMethodAccess(method), method.getName(), DescriptorFormat.getMethodDescriptor(method), null, null);

                for (TryCatchBlockNode tcb : tryCatchBlocks) tcb.accept(mv);

                mv.visitCode();

                for (AbstractInsnNode insn : instructions) insn.accept(mv);
                for (LineNumberNode line : lineNumbers) line.accept(mv);
                for (LocalVariableNode local : localVariables) local.accept(mv);

                mv.visitMaxs(0, 0);
                mv.visitEnd();

                super.visitEnd();
            }
        };

        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}