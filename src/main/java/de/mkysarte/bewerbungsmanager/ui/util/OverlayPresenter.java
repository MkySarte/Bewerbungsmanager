package de.mkysarte.bewerbungsmanager.ui.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class OverlayPresenter {

    private StackPane overlay;
    private VBox overlayShell;
    private StackPane overlayContent;

    public void attachHost(StackPane overlay, VBox overlayShell, StackPane overlayContent) {
        this.overlay = overlay;
        this.overlayShell = overlayShell;
        this.overlayContent = overlayContent;
    }

    public void show(OverlayRequest request) {
        if (overlay == null || overlayShell == null || overlayContent == null) {
            throw new IllegalStateException("Overlay host is not attached.");
        }

        VBox panel = new VBox(10);
        panel.setFillWidth(true);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setMinHeight(request.minHeight());
        panel.getStyleClass().addAll("admin-overlay-panel", "confirm-overlay-panel");
        panel.getStyleClass().add(variantStyleClass(request.variant()));

        if (!request.title().isBlank()) {
            Label title = new Label(request.title());
            title.getStyleClass().add("overlay-title");
            panel.getChildren().add(title);
        }

        if (!request.subtitle().isBlank()) {
            Label subtitle = new Label(request.subtitle());
            subtitle.getStyleClass().add("overlay-subtitle");
            subtitle.setWrapText(true);
            panel.getChildren().add(subtitle);
        }

        if (!request.message().isBlank()) {
            Label message = new Label(request.message());
            message.getStyleClass().addAll("overlay-warning", variantMessageClass(request.variant()));
            message.setWrapText(true);
            panel.getChildren().add(message);
        }

        if (request.customContent() != null) {
            panel.getChildren().add(request.customContent());
        }

        if (!request.actions().isEmpty()) {
            HBox actions = new HBox(8);
            actions.getStyleClass().add("admin-overlay-actions");
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.setMaxWidth(Double.MAX_VALUE);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            actions.getChildren().add(spacer);

            for (OverlayAction action : request.actions()) {
                Button button = new Button(action.label());
                button.getStyleClass().add(roleStyleClass(action.role()));
                button.setOnAction(event -> {
                    button.setDisable(true);
                    if (action.closeAfterAction()) {
                        hide();
                    }
                    if (action.action() != null) {
                        action.action().run();
                    }
                    if (!action.closeAfterAction()) {
                        Platform.runLater(() -> button.setDisable(false));
                    }
                });
                actions.getChildren().add(button);
            }

            panel.getChildren().add(actions);
        }

        overlayContent.getChildren().setAll(panel);
        overlayContent.setAlignment(Pos.CENTER);
        StackPane.setAlignment(panel, Pos.CENTER);
        overlayContent.setMaxWidth(Region.USE_PREF_SIZE);
        overlayContent.setMaxHeight(Region.USE_PREF_SIZE);
        overlayShell.setMaxHeight(Region.USE_PREF_SIZE);
        overlayShell.setAlignment(Pos.CENTER);
        overlay.setVisible(true);
        overlay.setManaged(true);
    }

    public void hide() {
        if (overlayContent != null) {
            overlayContent.getChildren().clear();
        }
        if (overlay != null) {
            overlay.setVisible(false);
            overlay.setManaged(false);
        }
    }

    private String roleStyleClass(OverlayActionRole role) {
        return switch (role) {
            case PRIMARY -> "btn-primary";
            case DANGER -> "btn-danger";
            case GHOST -> "ghost-btn";
        };
    }

    private String variantStyleClass(OverlayVariant variant) {
        return switch (variant) {
            case INFO -> "confirm-overlay-panel-info";
            case WARNING -> "confirm-overlay-panel-warning";
            case CONFIRM -> "confirm-overlay-panel-confirm";
            case DANGER -> "confirm-overlay-panel-danger";
        };
    }

    private String variantMessageClass(OverlayVariant variant) {
        return switch (variant) {
            case INFO -> "overlay-warning-info";
            case WARNING, CONFIRM -> "overlay-warning-warning";
            case DANGER -> "overlay-warning-danger";
        };
    }
}
