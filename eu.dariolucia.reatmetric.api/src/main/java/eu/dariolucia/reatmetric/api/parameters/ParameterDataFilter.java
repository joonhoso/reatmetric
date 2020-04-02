/*
 * Copyright (c) 2019.  Dario Lucia (dario.lucia@gmail.com)
 * All rights reserved.
 *
 * Right to reproduce, use, modify and distribute (in whole or in part) this library for demonstrations/trainings/study/commercial purposes shall be granted by the author in writing.
 */


package eu.dariolucia.reatmetric.api.parameters;

import eu.dariolucia.reatmetric.api.common.AbstractDataItemFilter;
import eu.dariolucia.reatmetric.api.model.AlarmState;
import eu.dariolucia.reatmetric.api.model.SystemEntity;
import eu.dariolucia.reatmetric.api.model.SystemEntityPath;
import eu.dariolucia.reatmetric.api.model.SystemEntityType;

import java.io.Serializable;
import java.util.*;

/**
 *
 * @author dario
 */
public final class ParameterDataFilter extends AbstractDataItemFilter<ParameterData> implements Serializable {
   
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

    private final SystemEntityPath parentPath;

	private final Set<SystemEntityPath> parameterPathList;

    private final Set<String> routeList;

    private final Set<Validity> validityList;

    private final Set<AlarmState> alarmStateList;

    private final Set<Integer> externalIdList;

    public ParameterDataFilter(SystemEntityPath parentPath, Collection<SystemEntityPath> pathList, Collection<String> routeList, Collection<Validity> validityList, Collection<AlarmState> alarmStateList, Collection<Integer> externalIdList) {
        this.parentPath = parentPath;
        if(pathList != null) {
            this.parameterPathList = Collections.unmodifiableSet(new LinkedHashSet<>(pathList));
        } else {
            this.parameterPathList = null;
        }
        if(routeList != null) {
            this.routeList = Collections.unmodifiableSet(new LinkedHashSet<>(routeList));
        } else {
            this.routeList = null;
        }
        if(validityList != null) {
            this.validityList = Collections.unmodifiableSet(new LinkedHashSet<>(validityList));
        } else {
            this.validityList = null;
        }
        if(alarmStateList != null) {
            this.alarmStateList = Collections.unmodifiableSet(new LinkedHashSet<>(alarmStateList));
        } else {
            this.alarmStateList = null;
        }
        if(externalIdList != null) {
            this.externalIdList = Collections.unmodifiableSet(new LinkedHashSet<>(externalIdList));
        } else {
            this.externalIdList = null;
        }
    }

    public SystemEntityPath getParentPath() {
        return parentPath;
    }

    public Set<SystemEntityPath> getParameterPathList() {
        return parameterPathList;
    }

    public Set<AlarmState> getAlarmStateList() {
        return alarmStateList;
    }

    public Set<Validity> getValidityList() {
        return validityList;
    }

    public Set<String> getRouteList() {
        return routeList;
    }

    public Set<Integer> getExternalIdList() {
        return externalIdList;
    }

    @Override
    public boolean isClear() {
        return this.parentPath == null && this.parameterPathList == null && this.routeList == null && this.alarmStateList == null && this.validityList == null && this.externalIdList == null;
    }

    @Override
    public boolean test(ParameterData item) {
        if(parentPath != null && !parentPath.isParentOf(item.getPath())) {
            return false;
        }
        if(parameterPathList != null && !parameterPathList.contains(item.getPath())) {
            return false;
        }
        if(alarmStateList != null && !alarmStateList.contains(item.getAlarmState())) {
            return false;
        }
        if(routeList != null && !routeList.contains(item.getRoute())) {
            return false;
        }
        if(validityList != null && !validityList.contains(item.getValidity())) {
            return false;
        }
        if(externalIdList != null && !externalIdList.contains(item.getExternalId())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean select(SystemEntity entity) {
        return entity.getType() == SystemEntityType.PARAMETER
                && (parentPath == null || parentPath.isParentOf(entity.getPath()) || entity.getPath().isParentOf(parentPath));
    }

    @Override
    public Class<ParameterData> getDataItemType() {
        return ParameterData.class;
    }
}
