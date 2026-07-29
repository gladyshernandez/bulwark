package com.bulwark.screening;

/** Outcome of a single screening layer. */
public enum Verdict {
    /** No injection signal found by this layer. */
    CLEAN,
    /** This layer detected a likely prompt injection. */
    INJECTION
}