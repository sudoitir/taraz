/**
 * {@link io.github.sudoitir.taraz.core.application.ports.outbound.AccountRepository} on JPA: entity,
 * mapper, ordered pessimistic locking (ADR-0015/0026/0042/0045), and the read-side balance repository
 * (ADR-0007) on plain {@code JdbcClient}.
 */
@NullMarked
package io.github.sudoitir.taraz.adapters.driven.persistence.account;

import org.jspecify.annotations.NullMarked;
