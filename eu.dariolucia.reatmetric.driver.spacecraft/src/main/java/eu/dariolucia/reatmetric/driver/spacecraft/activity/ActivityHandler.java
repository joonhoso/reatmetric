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

package eu.dariolucia.reatmetric.driver.spacecraft.activity;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.IPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.PacketDefinitionIndexer;
import eu.dariolucia.ccsds.encdec.structure.impl.DefaultPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.resolvers.DefaultValueFallbackResolver;
import eu.dariolucia.ccsds.encdec.structure.resolvers.PathLocationBasedResolver;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.reatmetric.api.processing.IActivityHandler;
import eu.dariolucia.reatmetric.api.processing.IProcessingModel;
import eu.dariolucia.reatmetric.api.processing.exceptions.ActivityHandlingException;
import eu.dariolucia.reatmetric.api.value.Array;
import eu.dariolucia.reatmetric.core.api.IServiceCoreContext;
import eu.dariolucia.reatmetric.driver.spacecraft.common.Constants;
import eu.dariolucia.reatmetric.driver.spacecraft.definition.SpacecraftConfiguration;
import eu.dariolucia.reatmetric.driver.spacecraft.services.ServiceBroker;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivityHandler {

    private static final int MAX_TC_PACKET_SIZE = 65536;

    private final String driverName;
    private final Instant epoch;
    private final SpacecraftConfiguration configuration;
    private final IServiceCoreContext context;
    private final ServiceBroker serviceBroker;
    private final PacketDefinitionIndexer encDecDefinitions;
    private final IPacketEncoder packetEncoder;
    private final Map<Long, PacketDefinition> externalId2packet;
    // Added later with the registerModel method call
    private IProcessingModel processingModel;


    public ActivityHandler(String driverName, Instant epoch, SpacecraftConfiguration configuration, IServiceCoreContext context, ServiceBroker serviceBroker, Definition encodingDecodingDefinitions) {
        this.driverName = driverName;
        this.epoch = epoch;
        this.configuration = configuration;
        this.context = context;
        this.serviceBroker = serviceBroker;
        this.encDecDefinitions = new PacketDefinitionIndexer(encodingDecodingDefinitions);
        this.packetEncoder = new DefaultPacketEncoder(encDecDefinitions, MAX_TC_PACKET_SIZE, epoch);
        // Create a map based on the external ID
        this.externalId2packet = new HashMap<>();
        for(PacketDefinition pd : encodingDecodingDefinitions.getPacketDefinitions()) {
            if(pd.getExternalId() != PacketDefinition.EXTERNAL_ID_NOT_SET && pd.getType().equals(configuration.getTcPacketConfiguration().getActivityTcPacketType())) {
                externalId2packet.put(pd.getExternalId(), pd);
            }
        }
    }

    public List<String> getSupportedActivityTypes() {
        return Collections.singletonList(configuration.getTcPacketConfiguration().getActivityTcPacketType());
    }

    public void executeActivity(IActivityHandler.ActivityInvocation activityInvocation) throws ActivityHandlingException {
        // get the encoding definition
        PacketDefinition defToEncode = externalId2packet.get((long) activityInvocation.getActivityId());
        if(defToEncode == null) {
            throw new ActivityHandlingException("Invocation for activity occurrence " + activityInvocation.getActivityOccurrenceId() + " of external ID " + activityInvocation.getActivityId()
            + " cannot be processed: TC packet definition not found");
        }
        // build the map and encode the activity as space packet contents
        Map<String, Object> convertedArgumentMap = new HashMap<>();
        for(Map.Entry<String, Object> entry : activityInvocation.getArguments().entrySet()) {
            if(entry.getValue() instanceof Array) {
                convertArrayRecords(defToEncode, entry.getKey(), (Array) entry.getValue(), convertedArgumentMap);
            } else {
                convertedArgumentMap.put(defToEncode.getId() + "." + entry.getKey(), entry.getValue());
            }
        }
        byte[] packetUserDataField = null;

        try {
            packetUserDataField = packetEncoder.encode(defToEncode.getId(), new DefaultValueFallbackResolver(new PathLocationBasedResolver(convertedArgumentMap)));
        } catch (EncodingException e) {
            throw new ActivityHandlingException("Cannot encode activity occurrence " + activityInvocation.getActivityOccurrenceId()
                    + " of external ID " + activityInvocation.getActivityId() + ": " + e.getMessage(), e);
        }
        // Construct the packet
        String packetInfo = defToEncode.getExtension();
        // TODO: construct the space packet using the information in the encoding definition and the configuration (override by activity properties)
        // TODO: build activity tracker
        // TODO: notify packet built to service broker
        // TODO: release packet to lower layer (TC layer), unless the activity is scheduled on-board (PUS 11, activity property)
    }

    private void convertArrayRecords(PacketDefinition defToEncode, String key, Array value, Map<String, Object> convertedArgumentMap) {
        // TODO
    }
}
