package com.bulwark.screening;

/**
 * How Bulwark acts on a detected injection.
 *
 * <ul>
 *   <li>{@link #BLOCK} — refuse the request and return a content-filter refusal;
 *       the prompt never reaches the upstream model.</li>
 *   <li>{@link #FLAG} — log the detection but forward the request unchanged. Used
 *       to measure false positives on benign corpora without breaking traffic.</li>
 * </ul>
 */
public enum ScreeningMode {
    BLOCK,
    FLAG
}