package io.gammax.api.enums;

public enum InjectAt {
    HEAD,
    RETURN,
    TAIL,

    INVOKE,
    INVOKE_ASSIGN,
    INVOKE_STRING,
    INVOKE_DYNAMIC,

    NEW,
    NEW_ARRAY,
    ANEW_ARRAY,
    MULTI_NEW_ARRAY,

    GET_FIELD,
    PUT_FIELD,
    GET_STATIC,
    PUT_STATIC,

    LOAD,
    STORE,
    IINC,

    JUMP,
    GOTO,
    LOOKUP_SWITCH,
    TABLE_SWITCH,

    THROW,
    CATCH,
    FINALLY,

    CONSTANT,
    CONSTANT_INT,
    CONSTANT_LONG,
    CONSTANT_FLOAT,
    CONSTANT_DOUBLE,
    CONSTANT_STRING,
    CONSTANT_CLASS,

    MONITOR_ENTER,
    MONITOR_EXIT,

    CHECKCAST,
    INSTANCEOF,

    ARRAY_LENGTH,
    ARRAY_LOAD,
    ARRAY_STORE,

    ADD,
    SUB,
    MUL,
    DIV,
    REM,
    NEG,
    SHL,
    SHR,
    USHR,
    AND,
    OR,
    XOR,

    LCMP,
    FCMPL,
    FCMPG,
    DCMPL,
    DCMPG,

    I2L, I2F, I2D,
    L2I, L2F, L2D,
    F2I, F2L, F2D,
    D2I, D2L, D2F,
    I2B, I2C, I2S,

    EARLY_RETURN,
    CONTINUE,
    BREAK
}
