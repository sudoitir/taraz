/**
 * The public {@code IntegrationEvent} contract (ADR-0009/0050) — the envelope plus one versioned
 * payload record per domain event type, kept deliberately separate from the internal {@code
 * DomainEvent} model so a change to the domain never breaks a consumer.
 */
@NullMarked
package io.github.sudoitir.taraz.adapters.driven.messaging.contract;

import org.jspecify.annotations.NullMarked;
