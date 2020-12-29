/*
 * Copyright (c)  2020 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.reatmetric.ui.udd;

import eu.dariolucia.reatmetric.api.IReatmetricSystem;
import eu.dariolucia.reatmetric.api.common.AbstractDataItem;
import eu.dariolucia.reatmetric.api.common.AbstractDataItemFilter;
import eu.dariolucia.reatmetric.api.common.IDataItemProvisionService;
import eu.dariolucia.reatmetric.api.common.RetrievalDirection;
import eu.dariolucia.reatmetric.api.common.exceptions.ReatmetricException;
import eu.dariolucia.reatmetric.api.events.EventData;
import eu.dariolucia.reatmetric.api.events.EventDataFilter;
import eu.dariolucia.reatmetric.api.model.SystemEntity;
import eu.dariolucia.reatmetric.api.model.SystemEntityType;
import eu.dariolucia.reatmetric.api.parameters.ParameterData;
import eu.dariolucia.reatmetric.api.parameters.ParameterDataFilter;
import eu.dariolucia.reatmetric.ui.ReatmetricUI;
import eu.dariolucia.reatmetric.ui.utils.FxUtils;
import eu.dariolucia.reatmetric.ui.utils.UserDisplayCoordinator;
import javafx.geometry.Insets;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressIndicator;
import org.controlsfx.control.PopOver;

import java.rmi.RemoteException;
import java.time.Instant;
import java.util.*;

public class PopoverChartController implements IChartDisplayController {

    private static final Timer UPDATE_TIMER = new Timer("ReatMetric UI - Chart Popover Update Thread", true);
    private static final int UPDATE_PERIOD = 5000;
    private static final int TIME_WINDOW = 60000;

    private final PopOver popOver;
    private final AbstractChartManager chartManager;
    private final XYChart<Instant, Number> chart;
    private final ParameterDataFilter parameterDataFilter;
    private final EventDataFilter eventDataFilter;

    private final TimerTask updateTask;

    public PopoverChartController(SystemEntity entity) throws ReatmetricException {
        // TODO: remove the popover and use a stage, with closable feature by pressing ESC
        popOver = new PopOver();
        popOver.setHeaderAlwaysVisible(true);
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_LEFT);

        Instant now = Instant.now();
        if(entity.getType() == SystemEntityType.PARAMETER) {
            parameterDataFilter = new ParameterDataFilter(null, Collections.singletonList(entity.getPath()), null, null, null, null);
            eventDataFilter = new EventDataFilter(null, Collections.emptyList(), null, null, null, null, null);
            popOver.setTitle("Parameter " + entity.getPath().asString());
            // Chart
            chart = new AreaChart<>(new InstantAxis(), new NumberAxis());
            chart.setAnimated(false);
            chart.getXAxis().setTickLabelsVisible(true);
            chart.getXAxis().setAutoRanging(false);
            chart.setLegendVisible(false);
            ((InstantAxis) chart.getXAxis()).setLowerBound(now.minusMillis(TIME_WINDOW - UPDATE_PERIOD));
            ((InstantAxis) chart.getXAxis()).setUpperBound(now.plusMillis(UPDATE_PERIOD));

            // Add to list
            chartManager = new XYTimeChartManager(o -> {
                // Update the subscriptions
                UserDisplayCoordinator.instance().filterUpdated();
            }, chart, false);
        } else if(entity.getType() == SystemEntityType.EVENT) {
            parameterDataFilter = new ParameterDataFilter(null, Collections.emptyList(), null, null, null, null);
            eventDataFilter = new EventDataFilter(null, Collections.singletonList(entity.getPath()), null, null, null, null, null);
            popOver.setTitle("Event " + entity.getPath().asString());
            // Chart
            chart = new ScatterChart<>(new InstantAxis(), new NumberAxis());
            chart.setAnimated(false);
            chart.getXAxis().setTickLabelsVisible(true);
            chart.getXAxis().setAutoRanging(false);
            chart.setLegendVisible(false);
            ((InstantAxis) chart.getXAxis()).setLowerBound(now.minusMillis(TIME_WINDOW - UPDATE_PERIOD));
            ((InstantAxis) chart.getXAxis()).setUpperBound(now.plusMillis(UPDATE_PERIOD));

            ((NumberAxis) chart.getYAxis()).setTickUnit(1.0);
            chart.getYAxis().setTickLabelsVisible(false);

            // Add to list
            chartManager = new XYScatterChartManager(o -> {
                // Update the subscriptions
                UserDisplayCoordinator.instance().filterUpdated();
            }, (ScatterChart<Instant, Number>)chart, false);
        } else {
            throw new ReatmetricException("Support is only for parameters and events");
        }
        chart.setPrefWidth(600);

        // Register on hidden
        popOver.setOnHidden(o -> {
            popupClosed(entity);
        });
        // Register to coordinator
        UserDisplayCoordinator.instance().register(this);

        // Add entity as path - This will trigger the subscription: AbstractChartManager -> UserDisplayCoordinator -> Processing Model subscription update
        chartManager.addItems(Collections.singletonList(entity.getPath().asString()));

        // Start autoupdate
        updateTask = new TimerTask() {
            @Override
            public void run() {
                FxUtils.runLater(() -> {
                    if(popOver.isShowing()) {
                        Instant now = Instant.now();
                        chartManager.setBoundaries(now.minusMillis(TIME_WINDOW - UPDATE_PERIOD), now.plusMillis(UPDATE_PERIOD));
                    }
                });
            }
        };
        UPDATE_TIMER.schedule(updateTask, UPDATE_PERIOD, UPDATE_PERIOD);

        // Now, add a progress indicator to the popover ...
        ProgressIndicator pi = new ProgressIndicator();
        pi.setPrefWidth(200);
        pi.setPrefHeight(200);
        pi.setPadding(new Insets(6,6,6,6));
        popOver.setContentNode(pi);
        // ... and retrieve data from the archive from now - TIME_WINDOW to now ...
        ReatmetricUI.threadPool(this.getClass()).submit(() -> {
            final List<AbstractDataItem> retrievedData = retrieveDataFromArchive(now);
            FxUtils.runLater(() -> {
                chartManager.plot(retrievedData);
                // Then, show the chart
                popOver.setContentNode(chart);
            });
        });
    }

    // TODO: what if the data that is arriving is old (see approach in user display for such type of plot, we cannot use Instant.now())... we should probably use a very large time
    private List<AbstractDataItem> retrieveDataFromArchive(Instant now) {
        List<AbstractDataItem> data = new LinkedList<>();
        try {
            if (!parameterDataFilter.getParameterPathList().isEmpty()) {
                data.addAll(ReatmetricUI.selectedSystem().getSystem().getParameterDataMonitorService().retrieve(Instant.ofEpochSecond(3600*24*365*1000L), 100, RetrievalDirection.TO_PAST, parameterDataFilter));
            } else {
                data.addAll(ReatmetricUI.selectedSystem().getSystem().getEventDataMonitorService().retrieve(Instant.ofEpochSecond(3600*24*365*1000L), 100, RetrievalDirection.TO_PAST, eventDataFilter));
            }
        } catch (ReatmetricException | RemoteException e) {
            // TODO: log
            e.printStackTrace();
        }
        Collections.reverse(data);
        return data;
    }

    private void popupClosed(SystemEntity parameter) {
        updateTask.cancel();
        UserDisplayCoordinator.instance().deregister(this);
        UserDisplayCoordinator.instance().filterUpdated();
        chartManager.clear();
    }

    public PopOver getPopOver() {
        return popOver;
    }

    @Override
    public void dispose() {
        popOver.hide();
    }

    @Override
    public void systemDisconnected(IReatmetricSystem system) {
        popOver.hide();
    }

    @Override
    public void systemConnected(IReatmetricSystem system) {
        // Nothing
    }

    @Override
    public EventDataFilter getCurrentEventFilter() {
        return eventDataFilter;
    }

    @Override
    public ParameterDataFilter getCurrentParameterFilter() {
        return parameterDataFilter;
    }

    @Override
    public boolean isLive() {
        return true;
    }

    @Override
    public void updateDataItems(List<? extends AbstractDataItem> items) {
        chartManager.plot((List<AbstractDataItem>) items);
    }
}
