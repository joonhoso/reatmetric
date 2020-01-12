/*
 * Copyright (c) 2020.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved.
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.core.impl;

import eu.dariolucia.reatmetric.api.archive.exceptions.ArchiveException;
import eu.dariolucia.reatmetric.api.common.FieldDescriptor;
import eu.dariolucia.reatmetric.api.common.IUniqueId;
import eu.dariolucia.reatmetric.api.common.LongUniqueId;
import eu.dariolucia.reatmetric.api.common.RetrievalDirection;
import eu.dariolucia.reatmetric.api.common.exceptions.ReatmetricException;
import eu.dariolucia.reatmetric.api.rawdata.*;
import eu.dariolucia.reatmetric.core.api.IRawDataBroker;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RawDataBrokerImpl implements IRawDataBroker, IRawDataProvisionService {

    private final IRawDataArchive archive;

    private final AtomicLong sequencer;

    private final List<RawDataSubscriptionManager> subscribers = new CopyOnWriteArrayList<>();
    private final Map<IRawDataSubscriber, RawDataSubscriptionManager> subscriberIndex = new ConcurrentHashMap<>();

    public RawDataBrokerImpl(IRawDataArchive archive) throws ArchiveException {
        this.archive = archive;
        this.sequencer = new AtomicLong();
        IUniqueId lastStoredUniqueId = archive.retrieveLastId();
        if(lastStoredUniqueId == null) {
            this.sequencer.set(0);
        } else {
            this.sequencer.set(lastStoredUniqueId.asLong());
        }
    }

    @Override
    public RawData getRawDataContents(IUniqueId uniqueId) throws ReatmetricException {
        // Access the archive and query it
        return archive.retrieve(uniqueId);
    }

    @Override
    public void subscribe(IRawDataSubscriber subscriber, RawDataFilter filter) {
        subscribe(subscriber, null, filter, null);
    }

    @Override
    public List<RawData> retrieve(Instant startTime, int numRecords, RetrievalDirection direction, RawDataFilter filter) throws ReatmetricException{
        // Access the archive and query it
        return archive.retrieve(startTime, numRecords, direction, filter);
    }

    @Override
    public List<RawData> retrieve(RawData startItem, int numRecords, RetrievalDirection direction, RawDataFilter filter) throws ReatmetricException {
        // Access the archive and query it
        return archive.retrieve(startItem, numRecords, direction, filter);
    }

    @Override
    public List<FieldDescriptor> getAdditionalFieldDescriptors() {
        return null;
    }

    @Override
    public void distribute(List<RawData> items, boolean store) throws ReatmetricException {
        if(store) {
            archive.store(items);
        }
        for(RawDataSubscriptionManager s : subscribers) {
            s.notifyItems(items);
        }
    }

    @Override
    public void subscribe(IRawDataSubscriber subscriber, Predicate<RawData> preFilter, RawDataFilter filter, Predicate<RawData> postFilter) {
        RawDataSubscriptionManager manager = subscriberIndex.get(subscriber);
        if(manager == null) {
            manager = new RawDataSubscriptionManager(subscriber, preFilter, filter, postFilter);
            subscriberIndex.put(subscriber, manager);
            subscribers.add(manager);
        } else {
            manager.update(preFilter, filter, postFilter);
        }
    }

    @Override
    public void unsubscribe(IRawDataSubscriber subscriber) {
        RawDataSubscriptionManager manager = this.subscriberIndex.remove(subscriber);
        if(manager != null) {
            this.subscribers.remove(manager);
            manager.terminate();
        }
    }

    @Override
    public IUniqueId nextRawDataId() {
        return new LongUniqueId(sequencer.incrementAndGet());
    }

    private static class RawDataSubscriptionManager {

        private final ExecutorService dispatcher = Executors.newSingleThreadExecutor();
        private final IRawDataSubscriber subscriber;
        private Predicate<RawData> preFilter;
        private Predicate<RawData> filter;
        private Predicate<RawData> postFilter;

        public RawDataSubscriptionManager(IRawDataSubscriber subscriber, Predicate<RawData> preFilter, RawDataFilter filter, Predicate<RawData> postFilter) {
            this.subscriber = subscriber;
            this.preFilter = preFilter;
            this.filter = new RawDataFilterPredicate(filter);
            this.postFilter = postFilter;
            sanitizeFilters();
        }

        public void notifyItems(List<RawData> items) {
            // TODO: implement timely and blocking policy
            dispatcher.submit(() -> {
                List<RawData> toNotify = filterItems(items);
                if(!toNotify.isEmpty()) {
                    subscriber.dataItemsReceived(toNotify);
                }
            });
        }

        private synchronized List<RawData> filterItems(List<RawData> items) {
            return items.stream().filter(preFilter).filter(filter).filter(postFilter).collect(Collectors.toList());
        }

        public synchronized void update(Predicate<RawData> preFilter, RawDataFilter filter, Predicate<RawData> postFilter) {
            this.preFilter = preFilter;
            this.filter = new RawDataFilterPredicate(filter);
            this.postFilter = postFilter;
            sanitizeFilters();
        }

        private void sanitizeFilters() {
            if(preFilter == null) {
                preFilter = (o) -> true;
            }
            if(postFilter == null) {
                postFilter = (o) -> true;
            }
        }

        public synchronized void terminate() {
            dispatcher.shutdownNow();
        }
    }

    private static class RawDataFilterPredicate implements Predicate<RawData> {

        private final RawDataFilter innerFilter;

        public RawDataFilterPredicate(RawDataFilter innerFilter) {
            this.innerFilter = innerFilter;
        }

        @Override
        public boolean test(RawData rawData) {
            if(innerFilter == null || innerFilter.isClear()) {
                return true;
            }
            if(innerFilter.getNameContains() != null && !rawData.getName().contains(innerFilter.getNameContains())) {
                return false;
            }
            if(innerFilter.getTypeList() != null && !innerFilter.getTypeList().contains(rawData.getType())) {
                return false;
            }
            if(innerFilter.getSourceList() != null && !innerFilter.getSourceList().contains(rawData.getSource())) {
                return false;
            }
            if(innerFilter.getRouteList() != null && !innerFilter.getRouteList().contains(rawData.getRoute())) {
                return false;
            }
            return innerFilter.getQualityList() == null || innerFilter.getQualityList().contains(rawData.getQuality());
        }
    }
}
