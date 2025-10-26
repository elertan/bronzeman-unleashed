package com.elertan.remote.firebase;

public enum FirebaseSSEType {
    Put("put"),
    Patch("patch"),
    KeepAlive("keep-alive"),
    Cancel("cancel"),
    AuthRevoked("auth_revoked");

    private final String raw;

    FirebaseSSEType(String raw) {
        this.raw = raw;
    }

    public String raw() {
        return raw;
    }

    public static FirebaseSSEType fromRaw(String raw) {
        if (raw == null) return null;
        for (FirebaseSSEType t : values()) {
            if (t.raw.equalsIgnoreCase(raw)) {
                return t;
            }
        }
        return null; // or throw IllegalArgumentException
    }
}