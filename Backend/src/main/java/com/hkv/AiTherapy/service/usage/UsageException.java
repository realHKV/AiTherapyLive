package com.hkv.AiTherapy.service.usage;

public final class UsageException extends RuntimeException {
    public enum Code { USER_LIMIT, CAPACITY_LIMIT, ACCOUNTING_UNAVAILABLE, IDEMPOTENCY_CONFLICT }
    private final Code code;
    public UsageException(Code code) { super(code.name()); this.code = code; }
    public Code code() { return code; }
}
