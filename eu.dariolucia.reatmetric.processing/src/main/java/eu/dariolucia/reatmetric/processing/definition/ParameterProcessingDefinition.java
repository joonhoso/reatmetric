/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes
 * shall be granted by the author in writing.
 */

package eu.dariolucia.reatmetric.processing.definition;

import eu.dariolucia.reatmetric.api.common.ValueTypeEnum;

import javax.xml.bind.annotation.*;
import java.util.LinkedList;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class ParameterProcessingDefinition {

    @XmlID
    @XmlAttribute(required = true)
    private int id;

    @XmlAttribute(required = true)
    private String location;

    @XmlAttribute
    private String description = "";

    @XmlAttribute(name = "raw_type",required = true)
    private ValueTypeEnum rawType;

    @XmlAttribute(name = "eng_type", required = true)
    private ValueTypeEnum engineeringType;

    @XmlAttribute(name = "eng_unit", required = false)
    private String unit;

    @XmlElement
    private ValidityDefinition validity;

    @XmlElementWrapper(name = "calibration")
    @XmlElements({
            @XmlElement(name="xy",type=XYCalibration.class),
            @XmlElement(name="poly",type=PolyCalibration.class),
            @XmlElement(name="log",type=LogCalibration.class),
            @XmlElement(name="enum",type=EnumCalibration.class),
            @XmlElement(name="expression",type=ExpressionCalibration.class),
            @XmlElement(name="external",type=ExternalCalibration.class),
    })
    private CalibrationDefinition calibration;

    @XmlElementWrapper(name = "checks")
    @XmlElements({
            @XmlElement(name="limit",type=LimitCheck.class),
            @XmlElement(name="expected",type=ExpectedCheck.class),
            @XmlElement(name="delta",type=DeltaCheck.class),
            @XmlElement(name="expression",type=ExpressionCheck.class),
            @XmlElement(name="external",type=ExternalCheck.class),
    })
    private List<CheckDefinition> checks = new LinkedList<>();

    @XmlElement(name = "expression")
    private ExpressionDefinition expression;

    public ParameterProcessingDefinition() {
        // For deserialisation
    }

    public ParameterProcessingDefinition(int id, String description, String location, ValueTypeEnum rawType, ValueTypeEnum engineeringType, String unit, ValidityDefinition validity, CalibrationDefinition calibration, List<CheckDefinition> checks, ExpressionDefinition expression) {
        this.id = id;
        this.description = description;
        this.location = location;
        this.rawType = rawType;
        this.engineeringType = engineeringType;
        this.unit = unit;
        this.validity = validity;
        this.calibration = calibration;
        this.checks = checks;
        this.expression = expression;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ValueTypeEnum getRawType() {
        return rawType;
    }

    public void setRawType(ValueTypeEnum rawType) {
        this.rawType = rawType;
    }

    public ValueTypeEnum getEngineeringType() {
        return engineeringType;
    }

    public void setEngineeringType(ValueTypeEnum engineeringType) {
        this.engineeringType = engineeringType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public ValidityDefinition getValidity() {
        return validity;
    }

    public void setValidity(ValidityDefinition validity) {
        this.validity = validity;
    }

    public CalibrationDefinition getCalibration() {
        return calibration;
    }

    public void setCalibration(CalibrationDefinition calibration) {
        this.calibration = calibration;
    }

    public List<CheckDefinition> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckDefinition> checks) {
        this.checks = checks;
    }

    public ExpressionDefinition getExpression() {
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }
}
