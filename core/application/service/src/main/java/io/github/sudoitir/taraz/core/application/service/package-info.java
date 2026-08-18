/**
 * CQRS write side: use-case handlers orchestrating domain + ports — no domain rules, no transport or
 * persistence details. Spring stereotype and constructor injection only, never Spring Data or the
 * transaction API (ADR-0006, ADR-0007, ADR-0039).
 */
@NullMarked
package io.github.sudoitir.taraz.core.application.service;

import org.jspecify.annotations.NullMarked;
