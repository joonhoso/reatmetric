/*
 * Copyright (c) 2020.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved.
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.driver.test;

import eu.dariolucia.reatmetric.api.common.Pair;
import eu.dariolucia.reatmetric.api.processing.IProcessingModel;
import eu.dariolucia.reatmetric.api.processing.input.EventOccurrence;
import eu.dariolucia.reatmetric.api.processing.input.ParameterSample;
import eu.dariolucia.reatmetric.api.transport.ITransportConnector;
import eu.dariolucia.reatmetric.api.transport.ITransportSubscriber;
import eu.dariolucia.reatmetric.api.transport.TransportConnectionStatus;
import eu.dariolucia.reatmetric.api.transport.TransportStatus;
import eu.dariolucia.reatmetric.api.value.BitString;
import eu.dariolucia.reatmetric.api.value.ValueTypeEnum;
import eu.dariolucia.reatmetric.processing.definition.EventProcessingDefinition;
import eu.dariolucia.reatmetric.processing.definition.ParameterProcessingDefinition;
import eu.dariolucia.reatmetric.processing.definition.ProcessingDefinition;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class TelemetryTransportConnectorImpl implements ITransportConnector {

    private final String name;
    private final String[] routes;
    private final List<ITransportSubscriber> subscriber = new CopyOnWriteArrayList<>();
    private final ProcessingDefinition definitions;
    private final IProcessingModel model;
    private final ExecutorService notifier = Executors.newSingleThreadExecutor();
    private volatile boolean connected;
    private volatile boolean initialised;
    private volatile String message;
    private volatile Thread generator;

    public TelemetryTransportConnectorImpl(String name, String[] routes, ProcessingDefinition definitions, IProcessingModel model) {
        this.name = name;
        this.routes = routes;
        this.definitions = definitions;
        this.model = model;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return "Connector " + name + ": " + Arrays.toString(routes);
    }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    @Override
    public void initialise(Map<String, Object> properties) {
        initialised = true;
        message = "Initialised";
        notifyState();
    }

    @Override
    public void connect() {
        connected = true;
        message = "Connected";
        notifyState();
        startTmGeneration();
    }

    private void startTmGeneration() {
        this.generator = new Thread(() -> {
            int paramCurrentIdx = 0;
            int eventCurrentIdx = 0;
            while(connected && this.generator == Thread.currentThread()) {
                List<ParameterSample> samples = new LinkedList<>();
                for(int i = 0; i < 80; ++i) {
                    samples.add(generateTmSample(paramCurrentIdx));
                    if(++paramCurrentIdx >= definitions.getParameterDefinitions().size()) {
                        paramCurrentIdx = 0;
                        message = "Wrapping around parameters";
                        notifyState();
                    }
                }
                if(model != null) {
                    model.injectParameters(samples);
                }
                for(int i = 0; i < 3; ++i) {
                    EventOccurrence event = generateEvent(eventCurrentIdx);
                    if(++eventCurrentIdx >= definitions.getEventDefinitions().size()) {
                        eventCurrentIdx = 0;
                        message = "Wrapping around events";
                        notifyState();
                    }
                    if(model != null) {
                        model.raiseEvent(event);
                    }
                }
            }
        });
        this.generator.start();
    }

    private EventOccurrence generateEvent(int eventCurrentIdx) {
        EventProcessingDefinition eventDef = definitions.getEventDefinitions().get(eventCurrentIdx);
        return EventOccurrence.of(eventDef.getId(), Instant.now(), Instant.now(), null, "Qual1", eventDef.hashCode(), routes[0], "SC1");
    }

    private ParameterSample generateTmSample(int currentIdx) {
        ParameterProcessingDefinition paramDef = definitions.getParameterDefinitions().get(currentIdx);
        return ParameterSample.of(paramDef.getId(), Instant.now(), Instant.now(), null, deriveValue(paramDef), routes[0]);
    }

    private Object deriveValue(ParameterProcessingDefinition paramDef) {
        switch(paramDef.getRawType()) {
            case REAL: return Math.sin(paramDef.hashCode() / (double) (Instant.now().toEpochMilli() % 10000));
            case SIGNED_INTEGER: return (long) (paramDef.hashCode() * (Instant.now().toEpochMilli() % 10000));
            case UNSIGNED_INTEGER: return (long) Math.abs(paramDef.hashCode() * (Instant.now().toEpochMilli() % 10000));
            case BOOLEAN: return Instant.now().toEpochMilli() % 2 == 0;
            case ENUMERATED: return Instant.now().toEpochMilli() % 8;
            case ABSOLUTE_TIME: return Instant.now().plusMillis(paramDef.hashCode());
            case RELATIVE_TIME: return Duration.ofMillis((Instant.now().toEpochMilli() % 10000));
            case CHARACTER_STRING: return "TEST" + (Instant.now().toEpochMilli() % 10);
            case OCTET_STRING: return ("BYTE" + Instant.now().toString()).getBytes();
            case BIT_STRING: return new BitString(new byte[] {0x00, (byte) 0xFF, (byte) 0xA3}, 23);
            default: return null;
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        message = "Disconnected";
        notifyState();
    }

    @Override
    public void abort() {
        connected = false;
        message = "Aborted";
        notifyState();
    }

    @Override
    public void dispose() {
        connected = false;
        initialised = false;
        message = "Disposed";
        notifyState();
    }

    private void notifyState() {
        for(ITransportSubscriber s : subscriber) {
            notifyState(s);
        }
    }

    @Override
    public Map<String, Pair<String, ValueTypeEnum>> getSupportedProperties() {
        return Collections.emptyMap();
    }

    @Override
    public void register(ITransportSubscriber listener) {
        if(!subscriber.contains(listener)) {
            subscriber.add(listener);
            notifyState(listener);
        }
    }

    private void notifyState(ITransportSubscriber listener) {
        TransportStatus status = buildTransportStatus();
        notifier.execute(() -> listener.status(status));
    }

    private TransportStatus buildTransportStatus() {
        return new TransportStatus(name, message, deriveStatus(), 0, 0);
    }

    private TransportConnectionStatus deriveStatus() {
        if(connected) {
            return TransportConnectionStatus.OPEN;
        } else {
            if(initialised) {
                return TransportConnectionStatus.IDLE;
            } else {
                return TransportConnectionStatus.NOT_INIT;
            }
        }
    }

    @Override
    public void deregister(ITransportSubscriber listener) {
        subscriber.remove(listener);
    }
}
