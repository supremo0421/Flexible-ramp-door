package dev.rampdoor;

public enum RampState {
    CLOSED, OPENING, OPEN, CLOSING;
    public boolean moving() { return this == OPENING || this == CLOSING; }
    public RampState safeEndpoint() { return this == OPENING ? CLOSED : this == CLOSING ? OPEN : this; }
}
