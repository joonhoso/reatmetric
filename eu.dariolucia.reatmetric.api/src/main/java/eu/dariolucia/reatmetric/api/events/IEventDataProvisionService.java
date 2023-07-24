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


package eu.dariolucia.reatmetric.api.events;

import eu.dariolucia.reatmetric.api.common.IDataItemProvisionService;
import eu.dariolucia.reatmetric.api.common.exceptions.ReatmetricException;
import eu.dariolucia.reatmetric.api.model.SystemEntityPath;

import java.rmi.RemoteException;

/**
 * This interface is a specialisation of the {@link IDataItemProvisionService}, for event occurrences. In addition, it provides
 * descriptors for the specified event.
 */
public interface IEventDataProvisionService extends IDataItemProvisionService<IEventDataSubscriber, EventDataFilter, EventData> {

    /**
     * Return the descriptor of the provided path.
     *
     * @param path the path of the system entity, for which the descriptor is needed
     * @return the {@link EventDescriptor} linked to the path
     * @throws ReatmetricException if the descriptor cannot be found or the path points to a different system entity type
     * @throws RemoteException in case of issues during the remote execution of the method
     */
    EventDescriptor getDescriptor(SystemEntityPath path) throws ReatmetricException, RemoteException;

    /**
     * Return the descriptor of the provided ID.
     *
     * @param externalId the ID of the system entity, for which the descriptor is needed
     * @return the {@link EventDescriptor} linked to the ID
     * @throws ReatmetricException if the descriptor cannot be found or the path points to a different system entity type
     * @throws RemoteException in case of issues during the remote execution of the method
     */
    EventDescriptor getDescriptor(int externalId) throws ReatmetricException, RemoteException;
}
