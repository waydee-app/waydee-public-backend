package com.waydee.social.application.event;

import java.util.Map;

/** Aktivite satırı kaydedildi — commit sonrası /topic/activity yayını için. */
public record ActivityRecordedEvent(Map<String, Object> payload) {
}
