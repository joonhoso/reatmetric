/*
 * Copyright (c) 2020.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved.
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.driver.spacecraft.definition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.time.Instant;

@XmlAccessorType(XmlAccessType.FIELD)
public class TimeCorrelationServiceConfiguration {

    @XmlAttribute(name = "generation-frame-period")
    private int generationPeriod = 256; // The generation period of the time packet in number of frames

    @XmlAttribute(name = "on-board-delay")
    private long onBoardDelay = 0L; // The amount of microseconds for the onboard delay

    @XmlAttribute(name = "sampling-rate-reported")
    private boolean samplingRateReported = false;

    @XmlElement(name = "time-format", required = true)
    private CucConfiguration timeFormat;

    public int getGenerationPeriod() {
        return generationPeriod;
    }

    public void setGenerationPeriod(int generationPeriod) {
        this.generationPeriod = generationPeriod;
    }

    public long getOnBoardDelay() {
        return onBoardDelay;
    }

    public void setOnBoardDelay(long onBoardDelay) {
        this.onBoardDelay = onBoardDelay;
    }

    public boolean isSamplingRateReported() {
        return samplingRateReported;
    }

    public void setSamplingRateReported(boolean samplingRateReported) {
        this.samplingRateReported = samplingRateReported;
    }
}
