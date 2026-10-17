package kr.compile;

import java.util.Locale;

public final class KrType {
    public enum Kind {
        VOID,
        BOOL,
        INT8, UINT8,
        INT16, UINT16,
        INT32, UINT32,
        INT64, UINT64,
        FLOAT32, FLOAT64,
        STRING,
        BYTE_SLICE,
        POINTER,
        STRUCT,
        UNKNOWN
    }

    public final Kind kind;
    public final String customName;

    public KrType(Kind kind) {
        this(kind, null);
    }

    public KrType(Kind kind, String customName) {
        this.kind = kind;
        this.customName = customName;
    }

    public static final KrType VOID = new KrType(Kind.VOID);
    public static final KrType BOOL = new KrType(Kind.BOOL);
    public static final KrType INT8 = new KrType(Kind.INT8);
    public static final KrType UINT8 = new KrType(Kind.UINT8);
    public static final KrType INT16 = new KrType(Kind.INT16);
    public static final KrType UINT16 = new KrType(Kind.UINT16);
    public static final KrType INT32 = new KrType(Kind.INT32);
    public static final KrType UINT32 = new KrType(Kind.UINT32);
    public static final KrType INT64 = new KrType(Kind.INT64);
    public static final KrType UINT64 = new KrType(Kind.UINT64);
    public static final KrType FLOAT32 = new KrType(Kind.FLOAT32);
    public static final KrType FLOAT64 = new KrType(Kind.FLOAT64);
    public static final KrType STRING = new KrType(Kind.STRING);
    public static final KrType BYTE_SLICE = new KrType(Kind.BYTE_SLICE);
    public static final KrType POINTER = new KrType(Kind.POINTER);
    public static final KrType UNKNOWN = new KrType(Kind.UNKNOWN);

    public static KrType fromString(String raw) {
        if (raw == null) return UNKNOWN;
        String s = raw.trim();
        String lower = s.toLowerCase(Locale.ROOT);

        // Void
        if (s.isEmpty() || lower.equals("void") || s.equals("()")) return VOID;

        // Bool
        if (lower.equals("bool") || lower.equals("boolean")) return BOOL;

        // 8-bit ints
        if (lower.equals("int8") || lower.equals("i8") || lower.equals("int8_t") || lower.equals("char") || lower.equals("signed char")) return INT8;
        if (lower.equals("uint8") || lower.equals("u8") || lower.equals("uint8_t") || lower.equals("byte") || lower.equals("unsigned char")) return UINT8;

        // 16-bit ints
        if (lower.equals("int16") || lower.equals("i16") || lower.equals("int16_t") || lower.equals("short") || lower.equals("short int")) return INT16;
        if (lower.equals("uint16") || lower.equals("u16") || lower.equals("uint16_t") || lower.equals("unsigned short")) return UINT16;

        // 32-bit ints
        if (lower.equals("int32") || lower.equals("i32") || lower.equals("int32_t") || lower.equals("int") || lower.equals("signed int") || lower.equals("long") || lower.equals("c_int")) return INT32;
        if (lower.equals("uint32") || lower.equals("u32") || lower.equals("uint32_t") || lower.equals("uint") || lower.equals("unsigned int") || lower.equals("unsigned long") || lower.equals("c_uint")) return UINT32;

        // 64-bit ints
        if (lower.equals("int64") || lower.equals("i64") || lower.equals("int64_t") || lower.equals("long long") || lower.equals("int64") || lower.equals("isize")) return INT64;
        if (lower.equals("uint64") || lower.equals("u64") || lower.equals("uint64_t") || lower.equals("unsigned long long") || lower.equals("usize") || lower.equals("uintptr") || lower.equals("size_t")) return UINT64;

        // Floats
        if (lower.equals("float") || lower.equals("f32") || lower.equals("float32") || lower.equals("c_float")) return FLOAT32;
        if (lower.equals("double") || lower.equals("f64") || lower.equals("float64") || lower.equals("c_double")) return FLOAT64;

        // Strings
        if (s.equals("string") || s.equals("const char*") || s.equals("char*") || s.equals("&str") || s.equals("String") || s.equals("std::string") || s.equals("*const c_char") || s.equals("*mut c_char")) return STRING;

        // Byte slices / arrays
        if (s.equals("[]byte") || s.equals("&[u8]") || s.equals("const uint8_t*") || s.equals("uint8_t*")) return BYTE_SLICE;

        // Generic pointers
        if (s.equals("void*") || s.equals("*mut c_void") || s.equals("*const c_void") || s.equals("unsafe.Pointer")) return POINTER;

        // Custom struct / type
        return new KrType(Kind.STRUCT, s);
    }

    public String toCType() {
        switch (kind) {
            case VOID: return "void";
            case BOOL: return "bool";
            case INT8: return "int8_t";
            case UINT8: return "uint8_t";
            case INT16: return "int16_t";
            case UINT16: return "uint16_t";
            case INT32: return "int32_t";
            case UINT32: return "uint32_t";
            case INT64: return "int64_t";
            case UINT64: return "uint64_t";
            case FLOAT32: return "float";
            case FLOAT64: return "double";
            case STRING: return "const char*";
            case BYTE_SLICE: return "const uint8_t*";
            case POINTER: return "void*";
            case STRUCT: return customName != null ? customName : "void*";
            default: return "int32_t";
        }
    }

    public String toCppType() {
        return toCType();
    }

    public String toRustType() {
        switch (kind) {
            case VOID: return "()";
            case BOOL: return "bool";
            case INT8: return "i8";
            case UINT8: return "u8";
            case INT16: return "i16";
            case UINT16: return "u16";
            case INT32: return "i32";
            case UINT32: return "u32";
            case INT64: return "i64";
            case UINT64: return "u64";
            case FLOAT32: return "f32";
            case FLOAT64: return "f64";
            case STRING: return "*const ::std::ffi::c_char";
            case BYTE_SLICE: return "*const u8";
            case POINTER: return "*mut ::std::ffi::c_void";
            case STRUCT: return customName != null ? customName : "*mut ::std::ffi::c_void";
            default: return "i32";
        }
    }

    public String toGoType() {
        switch (kind) {
            case VOID: return "";
            case BOOL: return "bool";
            case INT8: return "int8";
            case UINT8: return "uint8";
            case INT16: return "int16";
            case UINT16: return "uint16";
            case INT32: return "int32";
            case UINT32: return "uint32";
            case INT64: return "int64";
            case UINT64: return "uint64";
            case FLOAT32: return "float32";
            case FLOAT64: return "float64";
            case STRING: return "*C.char";
            case BYTE_SLICE: return "*C.uint8_t";
            case POINTER: return "unsafe.Pointer";
            case STRUCT: return customName != null ? "C.struct_" + customName : "unsafe.Pointer";
            default: return "int32";
        }
    }

    public String toGoFriendlyType() {
        switch (kind) {
            case VOID: return "";
            case BOOL: return "bool";
            case INT8: return "int8";
            case UINT8: return "uint8";
            case INT16: return "int16";
            case UINT16: return "uint16";
            case INT32: return "int32";
            case UINT32: return "uint32";
            case INT64: return "int64";
            case UINT64: return "uint64";
            case FLOAT32: return "float32";
            case FLOAT64: return "float64";
            case STRING: return "string";
            case BYTE_SLICE: return "[]byte";
            case POINTER: return "unsafe.Pointer";
            case STRUCT: return customName != null ? customName : "unsafe.Pointer";
            default: return "int32";
        }
    }

    @Override
    public String toString() {
        return customName != null ? customName : kind.name();
    }
}
