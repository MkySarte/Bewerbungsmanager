package de.mkysarte.bewerbungsmanager.ui.util;

public record OverlayAction(
        String label,
        OverlayActionRole role,
        Runnable action,
        boolean closeAfterAction
) {
    public OverlayAction {
        label = label == null ? "" : label;
        role = role == null ? OverlayActionRole.GHOST : role;
    }

    public static OverlayAction ghost(String label, Runnable action) {
        return new OverlayAction(label, OverlayActionRole.GHOST, action, true);
    }

    public static OverlayAction primary(String label, Runnable action) {
        return new OverlayAction(label, OverlayActionRole.PRIMARY, action, true);
    }

    public static OverlayAction danger(String label, Runnable action) {
        return new OverlayAction(label, OverlayActionRole.DANGER, action, true);
    }

    public static OverlayAction ghost(String label, Runnable action, boolean closeAfterAction) {
        return new OverlayAction(label, OverlayActionRole.GHOST, action, closeAfterAction);
    }

    public static OverlayAction primary(String label, Runnable action, boolean closeAfterAction) {
        return new OverlayAction(label, OverlayActionRole.PRIMARY, action, closeAfterAction);
    }

    public static OverlayAction danger(String label, Runnable action, boolean closeAfterAction) {
        return new OverlayAction(label, OverlayActionRole.DANGER, action, closeAfterAction);
    }
}
