/*
 * Copyright (c)  2023 Dario Lucia (https://www.dariolucia.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package eu.dariolucia.reatmetric.driver.socket;

/**
 * TODO: this device has a protocol supporting two ASCII connections, one for monitoring (asynch), one for commanding, fully
 * synchronous (request, response)
 * Command messages are:
 * - Send command to device subsystem: CMD [device subsystem name] [command code] [argument 1] [argument 2] on command connection
 * - Set device subsystem value: SET [device subsystem name] [parameter name] [parameter value] on command connection
 * Response messages are:
 * - Telemetry data: TLM [list of parameter values separated by space] on monitoring connection, 1 per second per device subsystem
 * - Positive ACK of command/set: ACK [device sybsystem name] on command connection
 * - Negative ACK: NOK on command connections
 */
public class AsciiDeviceDoubleConnection {
}
