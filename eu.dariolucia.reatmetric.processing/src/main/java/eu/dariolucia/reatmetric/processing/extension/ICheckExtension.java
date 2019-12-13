/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes
 * shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.processing.extension;

import eu.dariolucia.reatmetric.processing.definition.scripting.IBindingResolver;

import java.time.Instant;
import java.util.Map;

/**
 *
 */
public interface ICheckExtension {

    String getFunctionName();

    boolean check(Object currentValue, Instant generationTime, Map<String, String> properties, IBindingResolver resolver);
}
