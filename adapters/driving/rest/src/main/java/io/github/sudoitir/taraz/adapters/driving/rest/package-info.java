/**
 * Driving (inbound) adapters: REST controllers, request/response DTOs, exception mapping. They call inbound ports
 * only — never outbound ports (ADR-0006, enforced by ADR-0023).
 */
@NullMarked
package io.github.sudoitir.taraz.adapters.driving.rest;

import org.jspecify.annotations.NullMarked;
