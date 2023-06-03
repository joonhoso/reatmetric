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

package eu.dariolucia.reatmetric.driver.spacecraft.definition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

@XmlAccessorType(XmlAccessType.FIELD)
public class ServiceConfiguration {

    @XmlAttribute(name = "type", required = true)
    private String type;

    @XmlAttribute(name = "configuration", required = true)
    private String configuration = "";

    public String getType() {
        return type;
    }

    public ServiceConfiguration setType(String type) {
        this.type = type;
        return this;
    }

    public String getConfiguration() {
        return configuration;
    }

    public ServiceConfiguration setConfiguration(String configuration) {
        this.configuration = configuration;
        return this;
    }
}
