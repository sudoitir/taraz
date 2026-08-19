package io.github.sudoitir.taraz.container.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Exposes a shared, transaction-scoped {@link EntityManager} as an ordinary bean so the persistence
 * adapter's repositories can take it via constructor injection (the project's uniform DI style).
 * {@code @PersistenceContext} cannot target a constructor parameter — JPA restricts it to fields and
 * setter methods — so field/setter injection is the usual escape hatch; this bean lets every adapter
 * stay constructor-injected instead. {@link SharedEntityManagerCreator} produces exactly the same
 * transaction-aware proxy {@code @PersistenceContext} field injection would have.
 */
@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    EntityManager entityManager(EntityManagerFactory entityManagerFactory) {
        return SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    }
}
