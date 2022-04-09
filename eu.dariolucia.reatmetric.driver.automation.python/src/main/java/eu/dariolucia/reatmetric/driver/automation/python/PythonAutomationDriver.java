/*
 * Copyright (c)  2022 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.reatmetric.driver.automation.python;

import eu.dariolucia.reatmetric.api.processing.exceptions.ActivityHandlingException;
import eu.dariolucia.reatmetric.driver.automation.base.AbstractAutomationDriver;
import eu.dariolucia.reatmetric.driver.automation.base.common.IScriptExecutor;
import eu.dariolucia.reatmetric.driver.automation.python.common.Constants;
import eu.dariolucia.reatmetric.driver.automation.python.internal.PythonExecutor;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * This driver provides the capability to execute automation and automation procedures written in Python.
 * <p>
 * The driver provides a simple API to scripts under execution, to easily access archived and processing data.
 * <p>
 * As limitation related to the Python language, Python scripts will not return any value.
 */
public class PythonAutomationDriver extends AbstractAutomationDriver {

    public PythonAutomationDriver() {
        //
    }

    @Override
    protected InputStream getApiDataFileInputStream() {
        return this.getClass().getClassLoader().getResourceAsStream(Constants.API_PYTHON_RESOURCE_FILE);
    }

    @Override
    protected void cleanUp() {
        // Do nothing
    }

    @Override
    protected String getAutomationRoute() {
        return Constants.AUTOMATION_ROUTE;
    }

    @Override
    protected IScriptExecutor buildScriptExecutor(ActivityInvocation activityInvocation, String fileName, String contents) throws ActivityHandlingException {
        if (fileName.endsWith(Constants.PYTHON_EXTENSION)) {
            return new PythonExecutor(getDataSubscriptionManager(), getContext(), getApiData(), contents, activityInvocation, fileName);
        } else {
            throw new ActivityHandlingException("Script type of " + fileName + " not supported: extension not recognized");
        }
    }

    @Override
    public List<String> getSupportedActivityTypes() {
        return Collections.singletonList(Constants.T_SCRIPT_TYPE);
    }
}
