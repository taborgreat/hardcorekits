package com.hardcorekits.staff;

/**
 * The staff ladder. OWNER appoints; MOD enforces; TRAINEE proposes.
 *
 * <p>Owners are not appointed in game at all — being a server operator is what makes an
 * owner, so /op and /deop at the console are the whole owner lifecycle.
 */
public enum Role {
    OWNER,
    MOD,
    TRAINEE;

    /** Whether this role may approve bans and ban outright. */
    public boolean canBan() {
        return this == OWNER || this == MOD;
    }

    /**
     * Whether this role may appoint or dismiss someone of that role — strictly downhill
     * only. Owners manage mods and trainees, mods manage trainees, and nobody on the same
     * rung can touch each other.
     */
    public boolean canManage(Role other) {
        return switch (this) {
            case OWNER -> other != OWNER;
            case MOD -> other == TRAINEE;
            case TRAINEE -> false;
        };
    }
}
