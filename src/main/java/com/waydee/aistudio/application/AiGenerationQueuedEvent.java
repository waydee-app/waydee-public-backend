package com.waydee.aistudio.application;

import java.util.UUID;

/**
 * "Bir üretim kuyruğa girdi" (V45).
 *
 * <p>⚠️ Olay yalnız <b>kimlik</b> taşır, entity taşımaz: dinleyici başka bir
 * thread'de ve başka bir transaction'da koşar; oraya taşınan bir entity
 * <b>detach</b> olur ve tembel alanları ({@code inputMediaIds}) okunduğunda
 * {@code LazyInitializationException} atardı.
 */
public record AiGenerationQueuedEvent(UUID generationId) {
}
