package com.waydee.common.events;

/**
 * Domain event yayınlama soyutlaması.
 * Bugün in-process (Spring events) çalışır; Redis Pub/Sub ya da Kafka'ya
 * geçiş bu arayüzün yeni bir implementasyonuyla yapılır — iş mantığı değişmez.
 */
public interface DomainEventPublisher {

    void publish(Object event);
}
