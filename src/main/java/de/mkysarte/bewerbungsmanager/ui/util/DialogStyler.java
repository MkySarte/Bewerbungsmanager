package de.mkysarte.bewerbungsmanager.ui.util;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;

public final class DialogStyler {

    private DialogStyler() {
    }

    public static void apply(DialogPane pane, boolean dark) {
        var cssUrl = DialogStyler.class.getResource("/styles/app.css");
        if (cssUrl != null && !pane.getStylesheets().contains(cssUrl.toExternalForm())) {
            pane.getStylesheets().add(cssUrl.toExternalForm());
        }
        if (!pane.getStyleClass().contains("modern-dialog")) {
            pane.getStyleClass().add("modern-dialog");
        }
        pane.getStyleClass().remove("dark");
        if (dark) {
            pane.getStyleClass().add("dark");
        }
    }

    /**
     * Macht aus einem Standarddialog einen, der zur Anwendung passt.
     *
     * <p>{@link StageStyle#TRANSPARENT} nimmt dem Fenster den Systemrahmen; die abgerundete
     * Fläche und der Schlagschatten kommen aus der Stilklasse {@code .modern-dialog}.
     *
     * <p>Ohne die transparente Szenenfüllung bliebe hinter den abgerundeten Ecken ein weißer
     * Kasten stehen. Die Szene entsteht erst beim Anzeigen, deshalb erst dort.
     *
     * <p>Ein solcher Dialog hat <b>keine Titelleiste und kein Fensterkreuz</b> — er muss über
     * seine Schaltflächen beantwortbar sein.
     */
    public static void prepare(Dialog<?> dialog, boolean dark) {
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setGraphic(null);
        apply(dialog.getDialogPane(), dark);
        dialog.setOnShowing(event -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
        });
    }
}
