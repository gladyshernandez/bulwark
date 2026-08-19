package com.bulwark.screening;

/**
 * What to do when a screening layer can't run (its sidecar or model is unavailable) and returns
 * {@link Verdict#DEGRADED}.
 */
public enum FailMode {
    /** Fail open: forward the request and record the gap - availability over caution. */
    OPEN,
    /** Fail closed: refuse the request because it couldn't be fully screened - caution over availability. */
    CLOSED
}
