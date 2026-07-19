package com.veil.confidential;

/**
 * A public-safe outcome returned by the confidential layer. It carries only what may be
 * broadcast: whether the operation was authorized, an opaque reference the ledger stores
 * (e.g. a commitment of the hidden target), and a human-readable note for narration.
 * It deliberately contains NO role, identity, or private target in the clear.
 *
 * @param authorized   whether the confidential check passed (e.g. a valid Shadow ordered it)
 * @param opaqueRef    an opaque commitment/handle safe to record publicly
 * @param message      narration-safe description
 */
public record ConfidentialResult(boolean authorized, String opaqueRef, String message) {

    public static ConfidentialResult ok(String opaqueRef, String message) {
        return new ConfidentialResult(true, opaqueRef, message);
    }

    public static ConfidentialResult denied(String message) {
        return new ConfidentialResult(false, "", message);
    }
}
