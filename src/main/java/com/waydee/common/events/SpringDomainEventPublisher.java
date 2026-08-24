package com.waydee.common.events;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    @Override
    public void publish(Object event) {
        delegate.publishEvent(event);
    }
}
