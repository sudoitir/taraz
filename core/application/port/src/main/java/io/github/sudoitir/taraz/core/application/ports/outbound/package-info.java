/**
 * Outbound ports: repository, unit-of-work, and idempotency-gate contracts implemented by driven
 * adapters. Interfaces only — no logic (ADR-0006). Driving adapters never see this package (ADR-0006's
 * hard rule, enforced by ADR-0023).
 */
@NullMarked
package io.github.sudoitir.taraz.core.application.ports.outbound;

import org.jspecify.annotations.NullMarked;
