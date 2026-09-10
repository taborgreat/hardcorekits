package com.hardcorekits.game;

/**
 * The single enum every other system gates on.
 *
 * <pre>WAITING -> COUNTDOWN -> INVULNERABLE -> ACTIVE -> FEAST -> ENDING -> RESETTING</pre>
 */
public enum GameState {
    WAITING,
    COUNTDOWN,
    INVULNERABLE,
    ACTIVE,
    FEAST,
    /** Someone has won: the victory ceremony is running and nothing takes damage. */
    ENDING,
    RESETTING;

    /** Kit selection is open and players mill around the centre. */
    public boolean isPreGame() {
        return this == WAITING || this == COUNTDOWN;
    }

    /** The match is running: players are scattered and the alive set is meaningful. */
    public boolean isLive() {
        return this == INVULNERABLE || this == ACTIVE || this == FEAST;
    }

    /** Players may damage each other. FEAST is a world-event flag, not a combat-rule change. */
    public boolean isPvpEnabled() {
        return this == ACTIVE || this == FEAST;
    }
}
