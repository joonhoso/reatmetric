/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes
 * shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.processing.impl;

import eu.dariolucia.reatmetric.api.common.AbstractDataItem;
import eu.dariolucia.reatmetric.api.common.IUniqueId;
import eu.dariolucia.reatmetric.api.model.ISystemModelSubscriber;
import eu.dariolucia.reatmetric.api.model.SystemEntity;
import eu.dariolucia.reatmetric.api.model.SystemEntityPath;
import eu.dariolucia.reatmetric.api.parameters.ParameterData;
import eu.dariolucia.reatmetric.processing.IProcessingModel;
import eu.dariolucia.reatmetric.processing.IProcessingModelOutput;
import eu.dariolucia.reatmetric.processing.ProcessingModelException;
import eu.dariolucia.reatmetric.processing.definition.ProcessingDefinition;
import eu.dariolucia.reatmetric.processing.impl.graph.GraphModel;
import eu.dariolucia.reatmetric.processing.impl.operations.AbstractModelOperation;
import eu.dariolucia.reatmetric.processing.impl.operations.ParameterSampleProcessOperation;
import eu.dariolucia.reatmetric.processing.input.EventOccurrence;
import eu.dariolucia.reatmetric.processing.input.ParameterSample;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProcessingModelImpl implements IParameterResolver, IProcessingModel {

    private static final Logger LOG = Logger.getLogger(ProcessingModelImpl.class.getName());

    private final List<ISystemModelSubscriber> listeners = new CopyOnWriteArrayList<>();

    private final ProcessingDefinition processingDefinition;

    private final IProcessingModelOutput output;

    private final Map<Class<? extends AbstractDataItem>, AtomicLong> updateSequencerMap = new ConcurrentHashMap<>();

    private final GraphModel graphModel;

    private final BlockingQueue<ProcessingTask> updateTaskQueue = new ArrayBlockingQueue<ProcessingTask>(1000); // TODO: parametric

    private final ExecutorService taskProcessors = Executors.newFixedThreadPool(2); // TODO: parametric

    private final ExecutorService dispatcher = Executors.newFixedThreadPool(1);

    private final ExecutorService notifier = Executors.newFixedThreadPool(1);

    private final WorkingSet workingSet = new WorkingSet();

    private final Consumer<List<AbstractDataItem>> outputRedirector;

    public ProcessingModelImpl(ProcessingDefinition processingDefinition, IProcessingModelOutput output, Map<Class<? extends AbstractDataItem>, Long> initialSequencerMap) throws ProcessingModelException {
        this.processingDefinition = processingDefinition;
        this.output = output;
        // Initialise the sequencer
        if(initialSequencerMap != null) {
            for(Map.Entry<Class<? extends AbstractDataItem>, Long> entry : initialSequencerMap.entrySet()) {
                updateSequencerMap.put(entry.getKey(), new AtomicLong(entry.getValue()));
            }
        }
        // Build the graph model and compute the topological sort
        graphModel = new GraphModel(processingDefinition, this);
        graphModel.build();
        // Activate the dispatcher
        dispatcher.submit(this::doDispatch);
        // Create redirector that uses the asynchronous notifier
        outputRedirector = createOutputRedirector();
    }

    private Consumer<List<AbstractDataItem>> createOutputRedirector() {
        return items -> notifier.submit(() -> output.notifyUpdate(items));
    }

    private void doDispatch() {
        while(!dispatcher.isShutdown()) {
            try {
                // Get the task
                ProcessingTask toProcess = this.updateTaskQueue.take();
                // Prepare the task
                toProcess.prepareTask(graphModel);
                // Check if the working set allows the processing of the items (blocking call)
                this.workingSet.add(toProcess.getAffectedItems());
                // Ready to be processed, submit the task
                this.taskProcessors.submit(toProcess);
            } catch(Exception e) {
                LOG.log(Level.SEVERE, "Exception when dispatching processing tasks: " + e.getMessage(), e);
            }
        }
    }

    public long getNextId(Class<? extends AbstractDataItem> type) {
        return updateSequencerMap.computeIfAbsent(type, o -> new AtomicLong(0)).getAndIncrement();
    }

    @Override
    public void injectParameters(List<ParameterSample> sampleList) {
        // Build the list of operations to be performed
        List<AbstractModelOperation> operations = new LinkedList<>();
        for(ParameterSample ps : sampleList) {
            ParameterSampleProcessOperation injectOperation = new ParameterSampleProcessOperation(ps);
            operations.add(injectOperation);
        }
        // Create the processing task
        ProcessingTask taskToRun = new ProcessingTask(operations, outputRedirector, workingSet);
        // Add the task to be done to the queue
        updateTaskQueue.add(taskToRun);
        // Done
    }

    @Override
    public void raiseEvent(EventOccurrence event, List<ParameterSample> attachedParameters) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public IUniqueId startAction() {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public void enable(SystemEntityPath path, boolean recursive) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public void disable(SystemEntityPath path, boolean recursive) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public ProcessingDefinition getProcessingDefinition() {
        return this.processingDefinition;
    }

    /*
     * ISystemModelProvisionService implementation
     */

    @Override
    public void subscribe(ISystemModelSubscriber subscriber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unsubscribe(ISystemModelSubscriber subscriber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SystemEntity getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<SystemEntity> getContainedEntities(SystemEntityPath se) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SystemEntity getSystemEntityAt(SystemEntityPath path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SystemEntity getSystemEntityOf(int externalId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getExternalIdOf(SystemEntityPath path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SystemEntityPath getPathOf(int externalId) {
        throw new UnsupportedOperationException();
    }

    /*
     * IParameterResolver implementation
     */

    @Override
    public ParameterData resolve(int parameterExternalId) {
        throw new UnsupportedOperationException();
    }
}
