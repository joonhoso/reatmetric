/*
 * Copyright (c)  2020 Dario Lucia (https://www.dariolucia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.dariolucia.reatmetric.api.transport;

import eu.dariolucia.reatmetric.api.common.Pair;
import eu.dariolucia.reatmetric.api.model.AlarmState;
import eu.dariolucia.reatmetric.api.transport.exceptions.TransportException;
import eu.dariolucia.reatmetric.api.value.ValueTypeEnum;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This abstract class provides a basic skeleton for the implementation of {@link ITransportConnector} classes.
 */
abstract public class AbstractTransportConnector implements ITransportConnector {

    private static final Logger LOG = Logger.getLogger(AbstractTransportConnector.class.getName());
    public static final String LOGTEMPLATE_CONNECTION_ALARM_STATE_CHANGED = "Connection %s alarm state changed to %s";

    private volatile String name;
    private volatile String description;

    private final Timer bitrateTimer;

    private volatile long lastTxRate = 0;
    private volatile long lastRxRate = 0;
    private volatile AlarmState lastAlarmState = AlarmState.UNKNOWN;

    private final Map<String, Object> initialisationMap = new HashMap<>();
    private final Map<String, Pair<String, ValueTypeEnum>> initialisationDescriptionMap = new HashMap<>();

    private final List<ITransportSubscriber> subscribers = new CopyOnWriteArrayList<>();
    private volatile TransportConnectionStatus connectionStatus = TransportConnectionStatus.NOT_INIT;
    private volatile boolean prepared = false;
    private volatile boolean initialised = false;
    private volatile boolean busy = false;
    private volatile boolean autoReconnect = false;

    private volatile TransportStatus lastTransportStatus = null;

    protected AbstractTransportConnector(String name, String description) {
        this();
        setCharacteristics(name, description);
    }

    private AbstractTransportConnector() {
        this.bitrateTimer = new Timer(name + " Bitrate Timer", true);
        this.bitrateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(connectionStatus == TransportConnectionStatus.OPEN) {
                    Pair<Long, Long> rate = computeBitrate();
                    if(rate != null) {
                        updateRates(rate.getFirst(), rate.getSecond());
                    } else {
                        updateRates(0, 0);
                    }
                } else {
                    updateRates(0, 0);
                }
            }
        }, 2000, 2000);
    }

    protected void setCharacteristics(String name, String description) {
        this.name = name;
        this.description = description;
        this.lastTransportStatus = new TransportStatus(name, connectionStatus, lastTxRate, lastRxRate, lastAlarmState, autoReconnect);;
    }

    /**
     * Return a pair TX rate, RX rate in bits per second.
     *
     * @return the tx-rx pair
     */
    protected abstract Pair<Long, Long> computeBitrate();

    @Override
    public void prepare() {
        if(prepared) {
            LOG.log(Level.WARNING, "Transport connector " + getName() + " already prepared");
            return;
        }
        if(name == null || description == null) {
            throw new IllegalStateException("Name and description cannot be null");
        }
        addToInitialisationMap(initialisationMap, initialisationDescriptionMap);
        prepared = true;
    }

    protected void addToInitialisationMap(Map<String, Object> initialisationMap, Map<String, Pair<String, ValueTypeEnum>> initialisationDescriptionMap) {
        // Nothing, subclasses can override
    }

    protected Map<String, Object> getInitialisationMap() {
        return initialisationMap;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public TransportConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    public TransportStatus getLastTransportStatus() {
        return lastTransportStatus;
    }

    @Override
    public boolean isInitialised() {
        return initialised;
    }

    @Override
    public void setReconnect(boolean autoReconnect) {
        boolean toNotify = !Objects.equals(autoReconnect, this.autoReconnect);
        this.autoReconnect = autoReconnect;
        if(toNotify) {
            notifySubscribers();
        }
    }

    @Override
    public boolean isReconnect() {
        return this.autoReconnect;
    }

    @Override
    public void initialise(Map<String, Object> properties) {
        checkPrepared();
        initialisationMap.clear();
        initialisationMap.putAll(properties);
        updateInitialisation(true);
    }

    private void checkPrepared() {
        if(!prepared) {
            throw new IllegalStateException(getName() + " not prepared");
        }
    }

    @Override
    public Map<String, Pair<String, ValueTypeEnum>> getSupportedProperties() {
        checkPrepared();
        return Map.copyOf(initialisationDescriptionMap);
    }

    @Override
    public Map<String, Object> getCurrentProperties() {
        checkPrepared();
        return Map.copyOf(initialisationMap);
    }

    @Override
    public void connect() throws TransportException {
        checkPrepared();
        if(busy) {
            return;
        }
        busy = true;
        try {
            LOG.log(Level.INFO, "Transport connector " + name + " opening connection");
            doConnect();
        } finally {
            busy = false;
        }
    }

    protected abstract void doConnect() throws TransportException;

    @Override
    public final void disconnect() throws TransportException {
        checkPrepared();
        if(busy) {
            return;
        }
        busy = true;
        try {
            setReconnect(false); // Explicit invocation should stop reconnection
            LOG.log(Level.INFO, "Transport connector " + name + " disconnecting");
            doDisconnect();
        } finally {
            busy = false;
        }
    }

    /**
     * This method has the same effects as disconnect(), without clearing the autoreconnect flag.
     * It must be invoked by implementations wishing to perform a clean-up of the connection status, without affecting
     * the autoreconnect flag.
     */
    protected final void internalDisconnect() throws TransportException {
        checkPrepared();
        if(busy) {
            return;
        }
        busy = true;
        try {
            doDisconnect();
        } finally {
            busy = false;
        }
    }

    protected abstract void doDisconnect() throws TransportException;

    @Override
    public void register(ITransportSubscriber listener) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.fine("New subscriber received: " + listener);
        }
        this.subscribers.add(listener);
    }

    @Override
    public void deregister(ITransportSubscriber listener) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.fine("Subscriber to remove: " + listener);
        }
        this.subscribers.remove(listener);
    }

    @Override
    public void dispose() {
        checkPrepared();
        this.subscribers.clear();
        this.bitrateTimer.cancel();
        doDispose();
        this.initialised = false;
    }

    protected abstract void doDispose();

    private void notifySubscribers() {
        TransportStatus status = new TransportStatus(name, connectionStatus, lastTxRate, lastRxRate, lastAlarmState, autoReconnect);
        this.lastTransportStatus = status;
        this.subscribers.forEach((s) -> {
            try {
                if(LOG.isLoggable(Level.FINER)) {
                    LOG.finer("Sending update to subscriber " + s + ": " + status);
                }
                s.status(status);
            } catch(Exception e) {
                LOG.log(Level.WARNING, getName() + ": cannot notify subscriber " + s + ": " + e.getMessage(), e);
                // Protect the class, unregister
                deregister(s);
            }
        });
    }

    protected void updateConnectionStatus(TransportConnectionStatus status) {
        boolean toNotify = !Objects.equals(status, this.connectionStatus);
        this.connectionStatus = status;
        if(toNotify) {
            notifySubscribers();
        }
    }

    protected void updateAlarmState(AlarmState state) {
        boolean toNotify = !Objects.equals(state, this.lastAlarmState);
        this.lastAlarmState = state;
        if(toNotify) {
            switch (state) {
                case ALARM:
                case ERROR:
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format(LOGTEMPLATE_CONNECTION_ALARM_STATE_CHANGED, getName(), state), new Object[] { getName() });
                    }
                    break;
                case WARNING:
                case VIOLATED:
                case UNKNOWN:
                    if(LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format(LOGTEMPLATE_CONNECTION_ALARM_STATE_CHANGED, getName(), state), new Object[] { getName() });
                    }
                    break;
                default:
                    if(LOG.isLoggable(Level.INFO)) {
                        LOG.log(Level.INFO, String.format(LOGTEMPLATE_CONNECTION_ALARM_STATE_CHANGED, getName(), state), new Object[] { getName() });
                    }
                    break;
            }
            notifySubscribers();
        }
    }

    protected void updateRates(long txRate, long rxRate) {
        boolean toNotify = txRate != lastTxRate || rxRate != lastRxRate;
        this.lastRxRate = rxRate;
        this.lastTxRate = txRate;
        if(toNotify) {
            notifySubscribers();
        }
    }

    protected void updateInitialisation(boolean b) {
        boolean toNotify = initialised != b;
        initialised = b;
        if(toNotify) {
            notifySubscribers();
        }
    }
}
