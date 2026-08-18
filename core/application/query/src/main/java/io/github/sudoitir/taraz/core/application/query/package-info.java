/**
 * CQRS read side: query handlers, read DTOs, pagination. Driving adapters call this package
 * directly — queries never go through the application service (ADR-0006, ADR-0007).
 */
@NullMarked
package io.github.sudoitir.taraz.core.application.query;

import org.jspecify.annotations.NullMarked;
