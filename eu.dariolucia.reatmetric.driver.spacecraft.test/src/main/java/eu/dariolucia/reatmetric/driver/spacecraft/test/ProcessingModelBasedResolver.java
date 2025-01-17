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

package eu.dariolucia.reatmetric.driver.spacecraft.test;

import eu.dariolucia.ccsds.encdec.definition.*;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.time.RelativeTimeDescriptor;
import eu.dariolucia.ccsds.encdec.value.BitString;
import eu.dariolucia.reatmetric.api.common.Pair;
import eu.dariolucia.reatmetric.api.value.ValueTypeEnum;
import eu.dariolucia.reatmetric.api.value.ValueUtil;
import eu.dariolucia.reatmetric.processing.definition.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ProcessingModelBasedResolver implements IEncodeResolver {

    private final static Logger LOG = Logger.getLogger(ProcessingModelBasedResolver.class.getName());

    private final List<ParameterDefinition> parameterDefinitions;
    private final IEncodeResolver innerResolver;
    private final Map<Integer, List<Object>> acceptableValues;
    private final Map<String, Long> name2idInTmTc = new HashMap<>();
    private final Set<String> parameters = new HashSet<>();
    private final Map<Long, Object> cachedValue = new ConcurrentHashMap<>();

    public ProcessingModelBasedResolver(ProcessingDefinition definition, IEncodeResolver innerResolver, int parameterOffset, Definition encodingDefinitions) {
        this.parameterDefinitions = encodingDefinitions.getParameters();
        this.innerResolver = innerResolver;
        this.acceptableValues = new HashMap<>();
        // Acceptable values coming from textual calibrations
        for(ParameterProcessingDefinition ppd : definition.getParameterDefinitions()) {
            name2idInTmTc.put(extractName(ppd.getLocation()), (long) (ppd.getId() - parameterOffset));
            List<Object> acceptables = computeAcceptableRawValues(ppd);
            if(acceptables != null) {
                acceptableValues.put(ppd.getId() - parameterOffset, acceptables);
            }
        }
        // Parameters that must contain a reference to a parameter Id
        for(PacketDefinition pd : encodingDefinitions.getPacketDefinitions()) {
            if(pd.getType().equals("TM")) {
                lookForParameterIdFields(pd);
            }
        }
        // Some parameters need to have a very specific value: are those parameters which play a role in the applicability of calibrations.
        // These values should be computed and pre-cached immediately.
        // Parameters used in calibration applicability condition
        for(ParameterProcessingDefinition ppd : definition.getParameterDefinitions()) {
            if(ppd.getCalibrations() != null) {
                for (CalibrationDefinition cd : ppd.getCalibrations()) {
                    if (cd.getApplicability() != null) {
                        Pair<Long, Object> value = deriveApplicabilityValue(cd.getApplicability());
                        if (value != null && value.getSecond() != null) {
                            cachedValue.put(value.getFirst() - parameterOffset, value.getSecond());
                            break;
                        }
                    }
                }
            }
        }
    }

    private static String extractName(String location) {
        return location.substring(location.lastIndexOf('.') + 1);
    }

    private Pair<Long, Object> deriveApplicabilityValue(ValidityCondition applicability) {
        if(applicability.getMatch() != null) {
            long id = applicability.getMatch().getParameter().getId();
            if(applicability.getMatch().getValue() != null) {
                Object val = ValueUtil.parse(applicability.getMatch().getValueType(), applicability.getMatch().getValue());
                // If the match is against an engineering value and val is a string, there is the likelyhood that a textual calibration
                // is applied. Check the parameter and, if so, decalibrate.
                if(!applicability.getMatch().isUseRawValue() && val instanceof String && (applicability.getMatch().getParameter().getRawType() == ValueTypeEnum.ENUMERATED
                || applicability.getMatch().getParameter().getRawType() == ValueTypeEnum.SIGNED_INTEGER || applicability.getMatch().getParameter().getRawType() == ValueTypeEnum.UNSIGNED_INTEGER)) {
                    // Decalibrate val with the first textual calibration you find for the pointed parameter
                    val = decalibrate((String) val, applicability.getMatch().getParameter());
                }
                LOG.info("Preset parameter ID " + id + " with value " + val);
                return Pair.of(id, val);
            }
        }
        return null;
    }

    private Object decalibrate(String val, ParameterProcessingDefinition parameter) {
        for (CalibrationDefinition cd : parameter.getCalibrations()) {
            if (cd instanceof EnumCalibration) {
                EnumCalibration theCalib = (EnumCalibration) cd;
                for(EnumCalibrationPoint calibPoint : theCalib.getPoints()) {
                    if(calibPoint.getValue().equals(val)) {
                        if (parameter.getRawType() == ValueTypeEnum.ENUMERATED) {
                            return (int) calibPoint.getInput();
                        } else {
                            return calibPoint.getInput();
                        }
                    }
                }
            }
        }
        // Not found, so go for val = 0
        if (parameter.getRawType() == ValueTypeEnum.ENUMERATED) {
            return 0;
        } else {
            return Long.valueOf(0L);
        }
    }

    private void lookForParameterIdFields(PacketDefinition pd) {
        for(AbstractEncodedItem ai : pd.getStructure().getEncodedItems()) {
            visit(ai);
        }
    }

    private void visit(AbstractEncodedItem ai) {
        if(ai instanceof EncodedArray) {
            for(AbstractEncodedItem inner : ((EncodedArray) ai).getEncodedItems()) {
                visit(inner);
            }
        } else if(ai instanceof EncodedStructure) {
            for(AbstractEncodedItem inner : ((EncodedStructure) ai).getEncodedItems()) {
                visit(inner);
            }
        } else if(ai instanceof EncodedParameter) {
            if(((EncodedParameter) ai).getType() != null && ((EncodedParameter) ai).getType() instanceof ParameterType) {
                parameters.add(((ParameterType)((EncodedParameter) ai).getType()).getReference());
            }
            if(((EncodedParameter) ai).getLength() != null && ((EncodedParameter) ai).getLength() instanceof ParameterLength) {
                parameters.add(((ParameterLength)((EncodedParameter) ai).getLength()).getReference());
            }
            if(((EncodedParameter) ai).getLinkedParameter() != null && ((EncodedParameter) ai).getLinkedParameter() instanceof ReferenceLinkedParameter) {
                parameters.add(((ReferenceLinkedParameter)((EncodedParameter) ai).getLinkedParameter()).getReference());
            }
        }
    }

    private List<Object> computeAcceptableRawValues(ParameterProcessingDefinition ppd) {
        // If a calibration is defined, provide something within the calibration range, use first calibration
        if(ppd.getCalibrations() != null && !ppd.getCalibrations().isEmpty()) {
            List<Object> data = new ArrayList<>();
            CalibrationDefinition cd = ppd.getCalibrations().get(0);
            if(cd instanceof EnumCalibration) {
                EnumCalibration cal = (EnumCalibration) cd;
                for(EnumCalibrationPoint o : cal.getPoints()) {
                    data.add(o.getInput());
                }
            } else if(cd instanceof XYCalibration) {
                XYCalibration cal = (XYCalibration) cd;
                for(XYCalibrationPoint o : cal.getPoints()) {
                    data.add(o.getX());
                }
            }
            return data;
        }
        return null;
    }

    @Override
    public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(parameter.getLinkedParameter() != null && parameter.getLinkedParameter() instanceof FixedLinkedParameter) {
            Object cached = cachedValue.get(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
            if(cached != null) {
                // Probability to invalidate the cache: 10%
                if(Math.random() < 0.1) {
                    cachedValue.remove(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                }
                return (Boolean) cached;
            } else {
                List<Object> data = acceptableValues.get((int) ((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                if (data != null && !data.isEmpty()) {
                    boolean toReturn = randomBool(data);
                    cachedValue.put(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId(), toReturn);
                    return toReturn;
                }
            }
        }
        return innerResolver.getBooleanValue(parameter, location);
    }

    private boolean randomBool(List<Object> data) {
        int idx = (int) Math.floor(Math.random() * data.size());
        return (boolean) data.get(idx);
    }

    private int randomInt(List<Object> data) {
        int idx = (int) Math.floor(Math.random() * data.size());
        return ((Number) data.get(idx)).intValue();
    }

    private long randomLong(List<Object> data) {
        int idx = (int) Math.floor(Math.random() * data.size());
        return ((Number) data.get(idx)).longValue();
    }

    private double randomReal(List<Object> data) {
        int idx = (int) Math.floor(Math.random() * data.size());
        return ((Number) data.get(idx)).doubleValue();
    }

    @Override
    public int getEnumerationValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(parameters.contains(parameter.getId())) {
            // Parameter id
            return selectRandomParameter();
        }
        if(parameter.getLinkedParameter() != null && parameter.getLinkedParameter() instanceof FixedLinkedParameter) {
            Object cached = cachedValue.get(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
            if(cached != null) {
                // Probability to invalidate the cache: 10%
                if(Math.random() < 0.1) {
                    cachedValue.remove(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                }
                return (int) cached;
            } else {
                List<Object> data = acceptableValues.get((int) ((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                if (data != null && !data.isEmpty()) {
                    int toReturn = randomInt(data);
                    cachedValue.put(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId(), toReturn);
                    return toReturn;
                }
            }
        }
        return innerResolver.getEnumerationValue(parameter, location);
    }

    private int selectRandomParameter() {
        int idx = (int) Math.floor(Math.random() * parameterDefinitions.size());
        return (int) parameterDefinitions.get(idx).getExternalId();
    }

    @Override
    public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(parameters.contains(parameter.getId())) {
            // Parameter id
            return selectRandomParameter();
        }
        if(parameter.getLinkedParameter() != null && parameter.getLinkedParameter() instanceof FixedLinkedParameter) {
            Object cached = cachedValue.get(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
            if(cached != null) {
                // Probability to invalidate the cache: 10%
                if(Math.random() < 0.1) {
                    cachedValue.remove(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                }
                return (long) cached;
            } else {
                List<Object> data = acceptableValues.get((int) ((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                if (data != null && !data.isEmpty()) {
                    long toReturn = randomLong(data);
                    cachedValue.put(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId(), toReturn);
                    return toReturn;
                }
            }
        }
        return innerResolver.getSignedIntegerValue(parameter, location);
    }

    @Override
    public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(parameters.contains(parameter.getId())) {
            // Parameter id
            return selectRandomParameter();
        }
        if(parameter.getLinkedParameter() != null && parameter.getLinkedParameter() instanceof FixedLinkedParameter) {
            Object cached = cachedValue.get(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
            if(cached != null) {
                // Probability to invalidate the cache: 10%
                if(Math.random() < 0.1) {
                    cachedValue.remove(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                }
                return (long) cached;
            } else {
                List<Object> data = acceptableValues.get((int) ((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                if (data != null && !data.isEmpty()) {
                    long toReturn = randomLong(data);
                    cachedValue.put(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId(), toReturn);
                    return toReturn;
                }
            }
        }
        return innerResolver.getUnsignedIntegerValue(parameter, location);
    }

    @Override
    public double getRealValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        if(parameter.getLinkedParameter() != null && parameter.getLinkedParameter() instanceof FixedLinkedParameter) {
            Object cached = cachedValue.get(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
            if(cached != null) {
                return (double) cached;
            } else {
                List<Object> data = acceptableValues.get((int) ((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId());
                if (data != null && !data.isEmpty()) {
                    double toReturn = randomReal(data);
                    cachedValue.put(((FixedLinkedParameter) parameter.getLinkedParameter()).getParameter().getExternalId(), toReturn);
                    return toReturn;
                }
            }
        }
        return innerResolver.getRealValue(parameter, location);
    }

    @Override
    public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return innerResolver.getAbsoluteTimeValue(parameter, location);
    }

    @Override
    public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return innerResolver.getRelativeTimeValue(parameter, location);
    }

    @Override
    public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) throws EncodingException {
        return innerResolver.getBitStringValue(parameter, location, maxBitlength);
    }

    @Override
    public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) throws EncodingException {
        return innerResolver.getOctetStringValue(parameter, location, maxByteLength);
    }

    @Override
    public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) throws EncodingException {
        return innerResolver.getCharacterStringValue(parameter, location, maxStringLength);
    }

    @Override
    public Object getExtensionValue(EncodedParameter parameter, PathLocation location) throws EncodingException {
        return innerResolver.getExtensionValue(parameter, location);
    }

    @Override
    public AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) throws EncodingException {
        return innerResolver.getAbsoluteTimeDescriptor(parameter, location, value);
    }

    @Override
    public RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) throws EncodingException {
        return innerResolver.getRelativeTimeDescriptor(parameter, location, value);
    }

    public void updateParameterValue(String name, Object value) {
        if(this.name2idInTmTc.containsKey(name)) {
            long id = this.name2idInTmTc.get(name);
            cachedValue.put(id, value);
        } else {
            throw new RuntimeException("Parameter name " + name + " not found in value map");
        }
    }
}
