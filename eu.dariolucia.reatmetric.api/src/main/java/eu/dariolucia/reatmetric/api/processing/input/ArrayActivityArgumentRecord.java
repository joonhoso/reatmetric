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

package eu.dariolucia.reatmetric.api.processing.input;

import java.util.List;

/**
 * This class represents a record in a {@link ArrayActivityArgument}. This is basically a single instantiation of the defined group.
 */
public class ArrayActivityArgumentRecord {

    private final List<AbstractActivityArgument> elements;

    public ArrayActivityArgumentRecord(List<AbstractActivityArgument> elements) {
        this.elements = List.copyOf(elements);
    }

    public List<AbstractActivityArgument> getElements() {
        return elements;
    }

}