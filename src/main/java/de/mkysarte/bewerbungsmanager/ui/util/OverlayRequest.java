package de.mkysarte.bewerbungsmanager.ui.util;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.layout.Region;

public record OverlayRequest(
        String title,
        String subtitle,
        String message,
        OverlayVariant variant,
        List<OverlayAction> actions,
        Node customContent,
        double minHeight
) {
    public OverlayRequest {
        title = title == null ? "" : title;
        subtitle = subtitle == null ? "" : subtitle;
        message = message == null ? "" : message;
        variant = variant == null ? OverlayVariant.INFO : variant;
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static OverlayRequest info(String title, String subtitle, String message, OverlayAction action) {
        return builder()
                .title(title)
                .subtitle(subtitle)
                .message(message)
                .variant(OverlayVariant.INFO)
                .actions(List.of(action))
                .build();
    }

    public static OverlayRequest warning(String title, String subtitle, String message, List<OverlayAction> actions) {
        return builder()
                .title(title)
                .subtitle(subtitle)
                .message(message)
                .variant(OverlayVariant.WARNING)
                .actions(actions)
                .build();
    }

    public static OverlayRequest confirm(String title, String subtitle, String message, List<OverlayAction> actions) {
        return builder()
                .title(title)
                .subtitle(subtitle)
                .message(message)
                .variant(OverlayVariant.CONFIRM)
                .actions(actions)
                .build();
    }

    public static OverlayRequest danger(String title, String subtitle, String message, List<OverlayAction> actions) {
        return builder()
                .title(title)
                .subtitle(subtitle)
                .message(message)
                .variant(OverlayVariant.DANGER)
                .actions(actions)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String subtitle;
        private String message;
        private OverlayVariant variant = OverlayVariant.INFO;
        private List<OverlayAction> actions = List.of();
        private Node customContent;
        private double minHeight = Region.USE_COMPUTED_SIZE;

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder variant(OverlayVariant variant) {
            this.variant = variant;
            return this;
        }

        public Builder actions(List<OverlayAction> actions) {
            this.actions = actions;
            return this;
        }

        public Builder customContent(Node customContent) {
            this.customContent = customContent;
            return this;
        }

        public Builder minHeight(double minHeight) {
            this.minHeight = minHeight;
            return this;
        }

        public OverlayRequest build() {
            return new OverlayRequest(title, subtitle, message, variant, actions, customContent, minHeight);
        }
    }
}
