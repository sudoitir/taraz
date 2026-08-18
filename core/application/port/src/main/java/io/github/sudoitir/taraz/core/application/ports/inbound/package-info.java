/**
 * Inbound ports: the command and query contracts driving adapters call. Commands are the only write-side
 * input (ADR-0034) — no web DTO or transport type ever crosses this boundary.
 */
@NullMarked
package io.github.sudoitir.taraz.core.application.ports.inbound;

import org.jspecify.annotations.NullMarked;
