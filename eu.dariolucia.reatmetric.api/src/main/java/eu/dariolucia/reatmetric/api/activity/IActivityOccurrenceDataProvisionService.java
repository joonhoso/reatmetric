/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved.
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes shall be granted by the author in writing.
 */


package eu.dariolucia.reatmetric.api.activity;

import eu.dariolucia.reatmetric.api.common.IDataItemStateProvisionService;

/**
 *
 * @author dario
 */
public interface IActivityOccurrenceDataProvisionService extends IDataItemStateProvisionService<IActivityOccurrenceDataSubscriber, ActivityOccurrenceDataFilter, ActivityOccurrenceData> {
   
}