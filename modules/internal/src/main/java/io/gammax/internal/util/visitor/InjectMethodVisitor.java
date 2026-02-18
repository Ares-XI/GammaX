package io.gammax.internal.util.visitor;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Method;
import java.util.*;

public class InjectMethodVisitor extends MethodVisitor {

    private final Method method;
    private final Class<?> targetClass;

    public InsnList instructions;
    public final List<TryCatchBlockNode> tryCatchBlocks = new ArrayList<>();
    public final List<LocalVariableNode> localVariables = new ArrayList<>();
    public final List<LineNumberNode> lineNumbers = new ArrayList<>();

    public final Map<String, String> fieldMap = new HashMap<>();
    public final Map<String, String> methodMap = new HashMap<>();
    public final InsnList insnList = new InsnList();

    public int maxLocals;
    public int maxStack;

    public InjectMethodVisitor(Method method, Class<?> targetClass) {
        super(Opcodes.ASM9);
        this.method = method;
        this.targetClass = targetClass;
    }

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
        String mixinName = method.getDeclaringClass().getName().replace('.', '/');
        if (type.equals(mixinName)) type = targetClass.getName().replace('.', '/');
        insnList.add(new TypeInsnNode(opcode, type));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        String key = name + ":" + desc;
        String newOwner = fieldMap.get(key);
        String mixinName = method.getDeclaringClass().getName().replace('.', '/');

        if (newOwner != null) insnList.add(new FieldInsnNode(opcode, newOwner, name, desc));
        else if (owner.equals(mixinName)) insnList.add(new FieldInsnNode(opcode,targetClass.getName().replace('.', '/'), name, desc));
        else insnList.add(new FieldInsnNode(opcode, owner, name, desc));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        String key = name + ":" + desc;
        String newOwner = methodMap.get(key);
        String mixinName = method.getDeclaringClass().getName().replace('.', '/');

        if (newOwner != null) insnList.add(new MethodInsnNode(opcode, newOwner, name, desc, itf));
        else if (owner.equals(mixinName)) insnList.add(new MethodInsnNode(opcode, targetClass.getName().replace('.', '/'), name, desc, itf));
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
        if (value instanceof Type t) {
            String mixinName = method.getDeclaringClass().getName().replace('.', '/');
            if (t.getInternalName().equals(mixinName)) value = Type.getObjectType(targetClass.getName().replace('.', '/'));
        }
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
        String mixinName = method.getDeclaringClass().getName().replace('.', '/');
        if (desc.contains(mixinName)) desc = desc.replace(mixinName, targetClass.getName().replace('.', '/'));
        insnList.add(new MultiANewArrayInsnNode(desc, dims));
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        String mixinName = method.getDeclaringClass().getName().replace('.', '/');
        if (type != null && type.equals(mixinName)) type = targetClass.getName().replace('.', '/');
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
    public void visitMaxs(int maxStack, int maxLocals) {
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
    }

    @Override
    public void visitEnd() {
        instructions = insnList;
    }
}