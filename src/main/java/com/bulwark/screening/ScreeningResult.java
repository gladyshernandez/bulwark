package com.bulwark.screening;

/**
 * Outcome of screening a request, handed back to the controller.
 *
 * @param model    the requested model (echoed into a refusal), may be null
 * @param decision the decision from the layer that determined the action
 * @param action   what to do: {@link Action#ALLOW}/{@link Action#FLAG} forward,
 *                 {@link Action#BLOCK} refuses
 */
public record ScreeningResult(String model, ScreeningDecision decision, Action action) {

    /** True when the request must be refused instead of forwarded. */
    public boolean isBlocked() {
        return action == Action.BLOCK;
    }
}