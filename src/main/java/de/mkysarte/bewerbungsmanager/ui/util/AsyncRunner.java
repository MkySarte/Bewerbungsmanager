package de.mkysarte.bewerbungsmanager.ui.util;

import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hilfsmethoden für asynchrone Service-Aufrufe aus JavaFX-Controllern.
 *
 * Spring-Services dürfen NICHT auf dem JavaFX Application Thread aufgerufen werden,
 * da sie DB-Zugriffe machen und den UI-Thread blockieren würden.
 *
 * Beispiel:
 *   AsyncRunner.run(
 *       () -> bewerbungService.getAll(),
 *       list -> applicationList.setItems(FXCollections.observableList(list)),
 *       error -> showErrorDialog(error)
 *   );
 */
@Slf4j
public final class AsyncRunner {

    private AsyncRunner() {}

    /**
     * Führt {@code work} in einem Hintergrund-Thread aus.
     * {@code onSuccess} und {@code onError} werden auf dem JavaFX Application Thread aufgerufen.
     */
    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture
                .supplyAsync(work)
                .thenAccept(result -> Platform.runLater(() -> onSuccess.accept(result)))
                .exceptionally(ex -> {
                    log.error("Async-Fehler", ex);
                    Platform.runLater(() -> onError.accept(ex));
                    return null;
                });
    }

    /**
     * Führt eine Aktion ohne Rückgabewert im Hintergrund aus.
     */
    public static void run(Runnable work, Runnable onSuccess, Consumer<Throwable> onError) {
        CompletableFuture
                .runAsync(work)
                .thenRun(() -> Platform.runLater(onSuccess))
                .exceptionally(ex -> {
                    log.error("Async-Fehler", ex);
                    Platform.runLater(() -> onError.accept(ex));
                    return null;
                });
    }
}
