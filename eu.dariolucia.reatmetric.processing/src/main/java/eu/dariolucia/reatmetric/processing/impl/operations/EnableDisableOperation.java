/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved.
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.processing.impl.operations;

import eu.dariolucia.reatmetric.api.common.AbstractDataItem;
import eu.dariolucia.reatmetric.api.common.Pair;
import eu.dariolucia.reatmetric.api.model.SystemEntity;
import eu.dariolucia.reatmetric.processing.ProcessingModelException;
import eu.dariolucia.reatmetric.processing.impl.processors.AbstractSystemEntityProcessor;

import java.time.Instant;

public class EnableDisableOperation extends AbstractModelOperation<AbstractDataItem, AbstractSystemEntityProcessor> {

    private final Instant creationTime = Instant.now();

    private final int id;
    private final boolean enable;

    public EnableDisableOperation(int id, boolean enable) {
        this.enable = enable;
        this.id = id;
    }

    @Override
    public Instant getTime() {
        return creationTime;
    }

    @Override
    protected Pair<AbstractDataItem, SystemEntity> doProcess() throws ProcessingModelException {
        return enable ? getProcessor().enable() : getProcessor().disable();
    }

    @Override
    public int getSystemEntityId() {
        return id;
    }
}
