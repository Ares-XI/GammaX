package io.gammax.api.enums;

public enum InjectAt {
    HEAD(false),
    RETURN(false),
    TAIL(false),
    INVOKE(true),
    INVOKE_ASSIGN(true),
    INVOKE_STRING(true),
    FIELD(true),
    NEW(true),
    CONSTANT(true),
    JUMP(true),
    LOAD(true),
    STORE(true);

    InjectAt(boolean requiresTarget) {}
}
