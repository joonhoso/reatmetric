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

package eu.dariolucia.reatmetric.ui.utils;

import eu.dariolucia.reatmetric.api.value.ValueTypeEnum;
import eu.dariolucia.reatmetric.api.value.ValueUtil;

import java.util.function.Predicate;

public class ValueTypeBasedValidator implements Predicate<String> {

    private final ValueTypeEnum type;

    public ValueTypeBasedValidator(ValueTypeEnum type) {
        this.type = type;
    }

    @Override
    public boolean test(String o) {
        try {
            ValueUtil.parse(type, o);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getErrorMessage() {
        switch(type) {
            case UNSIGNED_INTEGER:
                return "Unsigned integer required";
            case SIGNED_INTEGER:
            case ENUMERATED:
                return "Integer required";
            case REAL:
                return "Real number required";
            case BOOLEAN:
                return "'true' or 'false' required";
            case OCTET_STRING:
                return "Byte sequence in hexadecimal format required";
            case BIT_STRING:
                return "Sequence of 0s and 1s required";
            case ABSOLUTE_TIME:
                return "Absolute time in the format yyyy-mm-ddThh:mm:ss.SSSZ required";
            case RELATIVE_TIME:
                return "Relative time in the format PTxHyMzz.zzzS required";
            case CHARACTER_STRING:
                return "String required";
            default:
                return "Unknown type error";
        }
    }
}
