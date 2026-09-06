package de.mkysarte.bewerbungsmanager.session;

import de.mkysarte.bewerbungsmanager.common.crypto.DbSession;
import de.mkysarte.bewerbungsmanager.common.security.ReferenceDataBootstrapService;
import de.mkysarte.bewerbungsmanager.user.entity.UserEntity;
import de.mkysarte.bewerbungsmanager.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Richtet die geöffnete Datenbank für die laufende Sitzung ein.
 *
 * <p>Der Benutzer der Sitzung steht schon fest — die Anmeldung ist vor dem Spring-Start
 * passiert. Hier wird nur noch dafür gesorgt, dass die passende Benutzerzeile und die
 * Referenzdaten (Status-Werte, Standardcontainer) in <i>dieser</i> Datenbank vorhanden sind,
 * und der {@link CurrentUserHolder} gesetzt.
 *
 * <p>Beide Datenbanken durchlaufen denselben Weg: {@code demo.db} bekommt den Initial-Benutzer,
 * {@code data.db} den einen echten. Ist idempotent.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class DesktopBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DesktopBootstrapRunner.class);

    private final UserRepository userRepository;
    private final ReferenceDataBootstrapService referenceDataBootstrapService;
    private final CurrentUserHolder currentUserHolder;
    private final DbSession dbSession;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = dbSession.username();

        UserEntity user = userRepository.findByUsername(username).orElseGet(() -> {
            log.info("Lege Benutzer '{}' in {} an", username, dbSession.databaseFile().getFileName());
            UserEntity created = new UserEntity();
            created.setUsername(username);
            return userRepository.save(created);
        });

        long count = userRepository.count();
        if (count > 1) {
            // Darf nie passieren: pro Datenbank existiert genau ein Konto.
            log.warn("Unerwartet {} Benutzer in {} — es sollte genau einer sein.",
                    count, dbSession.databaseFile().getFileName());
        }

        referenceDataBootstrapService.ensureMinimumReferenceData(user.getUserId());
        currentUserHolder.set(user.getUserId(), user.getUsername());

        log.info("Sitzung bereit: {} ({})", username, dbSession.demo() ? "Demo" : "eigenes Konto");
    }
}
