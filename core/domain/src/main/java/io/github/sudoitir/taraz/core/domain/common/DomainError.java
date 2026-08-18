package io.github.sudoitir.taraz.core.domain.common;

/**
 * A predicted domain failure: stable {@link ErrorCode} for assertions plus a human-readable message for
 * logs. Equality intentionally includes the message — two failures with the same code but different
 * context are different diagnostics, and tests compare codes via {@link #code()} anyway.
 */
public record DomainError(ErrorCode code, String message) {}
