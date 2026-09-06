package de.mkysarte.bewerbungsmanager.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * FXMLLoader mit Spring Dependency Injection.
 *
 * Alle FXML-Controller werden als Spring @Component behandelt —
 * Spring injiziert automatisch @Autowired-Services in die Controller.
 *
 * Verwendung:
 *   Parent view = springFXMLLoader.load("/fxml/Dashboard.fxml");
 */
@Component
@RequiredArgsConstructor
public class SpringFXMLLoader {

    private final ApplicationContext applicationContext;
    private final ThemeService themeService;

    public Parent load(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        // Spring übernimmt die Controller-Instanziierung
        loader.setControllerFactory(applicationContext::getBean);
        loader.setLocation(getClass().getResource(fxmlPath));

        if (loader.getLocation() == null) {
            throw new IOException("FXML nicht gefunden: " + fxmlPath);
        }

        Parent view = loader.load();
        themeService.apply(view);
        return view;
    }

    /**
     * Lädt FXML und gibt den typsicheren Controller zurück.
     */
    public <T> LoadResult<T> loadWithController(String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setControllerFactory(applicationContext::getBean);
        loader.setLocation(getClass().getResource(fxmlPath));

        if (loader.getLocation() == null) {
            throw new IOException("FXML nicht gefunden: " + fxmlPath);
        }

        Parent view = loader.load();
        themeService.apply(view);
        T controller = loader.getController();
        return new LoadResult<>(view, controller);
    }

    public record LoadResult<T>(Parent view, T controller) {}
}
