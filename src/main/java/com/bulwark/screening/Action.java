package com.bulwark.screening;

/** What Bulwark did with a request after screening. */
public enum Action {
    /** No injection detected - forwarded upstream. */
    ALLOW,
    /** Injection detected in BLOCK mode - refused, not forwarded. */
    BLOCK,
    /** Injection detected in FLAG mode - logged, then forwarded. */
    FLAG
}