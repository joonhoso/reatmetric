package eu.dariolucia.reatmetric.driver.spacecraft.test;

import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.time.RelativeTimeDescriptor;
import eu.dariolucia.ccsds.encdec.value.BitString;

import java.time.Duration;
import java.time.Instant;

public class RandomBasedResolved implements IEncodeResolver {
    private final AbsoluteTimeDescriptor absoluteTimeDescriptor = AbsoluteTimeDescriptor.newCucDescriptor(4, 3);
    private final RelativeTimeDescriptor relativeTimeDescriptor = RelativeTimeDescriptor.newCucDescriptor(4, 3);

    public RandomBasedResolved() {
    }

    public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) {
        return Math.random() > 0.5;
    }

    public int getEnumerationValue(EncodedParameter parameter, PathLocation location) {
        return (int) Math.floor(Math.random() * 3); // 0, 1 or 2
    }

    public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) {
        return (long) Math.floor(Math.random() * 6 - 3);
    }

    public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) {
        return (long) Math.floor(Math.random() * 6); // 0 to 5
    }

    public double getRealValue(EncodedParameter parameter, PathLocation location) {
        return Math.random() * 10.0;
    }

    public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) {
        return Instant.ofEpochMilli(System.currentTimeMillis());
    }

    public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) {
        return Duration.ofSeconds((int) (Math.random() * 1000));
    }

    public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) {
        return new BitString(new byte[(int)Math.ceil((double)maxBitlength / 8.0)], maxBitlength);
    }

    public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) {
        return new byte[maxByteLength];
    }

    public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) {
        return "0".repeat(Math.max(0, maxStringLength));
    }

    public Object getExtensionValue(EncodedParameter parameter, PathLocation location) {
        return null;
    }

    public AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) {
        return this.absoluteTimeDescriptor;
    }

    public RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) {
        return this.relativeTimeDescriptor;
    }
}
