package io.gammax.internal.util;

import io.gammax.api.util.TargetReference;
import io.gammax.api.util.At;
import org.objectweb.asm.tree.*;

import java.util.List;

public class TargetData {
    private final String owner;
    private final String name;
    private final String desc;
    private final boolean any;
    private final int varIndex;
    private final boolean searchByName;

    public TargetData(TargetReference target, At at) {
        this.any = target.owner() == void.class;
        this.owner = !any ? target.owner().getName().replace('.', '/') : null;
        this.name = target.name();
        this.desc = buildDescriptor(target, at);

        if (at == At.LOAD || at == At.STORE) {
            int parsedIndex = parseVarIndex(target.name());
            if (parsedIndex != -1) {
                this.varIndex = parsedIndex;
                this.searchByName = false;
            } else {
                this.varIndex = -1;
                this.searchByName = !target.name().isEmpty();
            }
        } else {
            this.varIndex = -1;
            this.searchByName = false;
        }
    }

    private String buildDescriptor(TargetReference target, At at) {
        return switch (at) {
            case INVOKE, INVOKE_ASSIGN, INVOKE_STRING, INVOKE_DYNAMIC ->
                    DescriptorFormat.getMethodDescriptor(target.signature());
            case FIELD ->
                    DescriptorFormat.getDescriptor(target.signature().result());
            case NEW, CHECKCAST, INSTANCEOF ->
                    target.owner() != void.class ?
                            target.owner().getName().replace('.', '/') : null;
            case LOAD, STORE ->
                    target.signature().result() != void.class ?
                            DescriptorFormat.getDescriptor(target.signature().result()) : null;
            default -> null;
        };
    }

    private int parseVarIndex(String name) {
        if (name == null || name.isEmpty()) return -1;
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public boolean matches(AbstractInsnNode insn) {
        return matches(insn, null);
    }

    public boolean matches(AbstractInsnNode insn, List<LocalVariableNode> localVariables) {
        if (any) return true;

        return switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                yield owner != null && owner.equals(methodInsn.owner) &&
                        name.equals(methodInsn.name) &&
                        desc.equals(methodInsn.desc);
            }
            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                yield owner != null && owner.equals(fieldInsn.owner) &&
                        name.equals(fieldInsn.name) &&
                        desc.equals(fieldInsn.desc);
            }
            case AbstractInsnNode.TYPE_INSN -> {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                yield owner != null && owner.equals(typeInsn.desc);
            }
            case AbstractInsnNode.VAR_INSN -> {
                VarInsnNode varInsn = (VarInsnNode) insn;

                if (varIndex != -1) {
                    yield varInsn.var == varIndex;
                }

                if (searchByName && localVariables != null) {
                    for (LocalVariableNode local : localVariables) {
                        if (local.name.equals(name) && local.index == varInsn.var) {
                            if (desc != null && !desc.isEmpty()) {
                                yield local.desc.equals(desc);
                            }
                            yield true;
                        }
                    }
                }

                yield false;
            }
            default -> false;
        };
    }

    public boolean requiresTarget() {
        return !any;
    }
}