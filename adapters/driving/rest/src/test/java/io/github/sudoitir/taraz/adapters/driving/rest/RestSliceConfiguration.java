package io.github.sudoitir.taraz.adapters.driving.rest;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Anchor for slice-test component scanning: this module holds no application class (the bootable one
 * lives in {@code container}), and {@code @WebMvcTest} needs a {@code @SpringBootApplication}-equivalent
 * to hang its filtered component scan on. Slice type-exclude filters keep it web-beans-only.
 */
@SpringBootApplication
class RestSliceConfiguration {}
