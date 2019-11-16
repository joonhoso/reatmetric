package eu.dariolucia.reatmetric.persist.services;

import eu.dariolucia.reatmetric.api.common.LongUniqueId;
import eu.dariolucia.reatmetric.api.common.RetrievalDirection;
import eu.dariolucia.reatmetric.api.events.EventData;
import eu.dariolucia.reatmetric.api.events.EventDataFilter;
import eu.dariolucia.reatmetric.api.events.IEventDataArchive;
import eu.dariolucia.reatmetric.api.messages.Severity;
import eu.dariolucia.reatmetric.api.model.SystemEntityPath;
import eu.dariolucia.reatmetric.persist.Archive;

import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EventDataArchive extends AbstractDataItemArchive<EventData, EventDataFilter> implements IEventDataArchive {

    private static final Logger LOG = Logger.getLogger(EventDataArchive.class.getName());

    private static final String STORE_STATEMENT = "INSERT INTO EVENT_DATA_TABLE(UniqueId,GenerationTime,ExternalId,Name,Path,Qualifier,ReceptionTime,Type,Route,Source,Severity,AdditionalData) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String LAST_ID_QUERY = "SELECT UniqueId FROM EVENT_DATA_TABLE ORDER BY UniqueId DESC FETCH FIRST ROW ONLY";
    private static final String RETRIEVE_BY_ID_QUERY = "SELECT UniqueId,GenerationTime,ExternalId,Name,Path,Qualifier,ReceptionTime,Type,Route,Source,Severity,AdditionalData FROM EVENT_DATA_TABLE WHERE UniqueId=?";

    public EventDataArchive(Archive controller) throws SQLException {
        super(controller);
    }

    @Override
    protected void setItemPropertiesToStatement(PreparedStatement storeStatement, EventData item) throws SQLException, IOException {
        storeStatement.setLong(1, item.getInternalId().asLong());
        storeStatement.setTimestamp(2, toTimestamp(item.getGenerationTime()));
        storeStatement.setInt(3, item.getExternalId());
        storeStatement.setString(4, item.getName());
        storeStatement.setString(5, item.getPath().asString());
        storeStatement.setString(6, item.getQualifier());
        storeStatement.setTimestamp(7, toTimestamp(item.getReceptionTime()));
        storeStatement.setString(8, item.getType());
        storeStatement.setString(9, item.getRoute());
        storeStatement.setString(10, item.getSource());
        storeStatement.setShort(11, (short) item.getSeverity().ordinal());
        storeStatement.setBlob(12, toInputstreamArray(item.getAdditionalFields()));
    }

    @Override
    protected PreparedStatement createStoreStatement(Connection connection) throws SQLException {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.finest(this + " - preparing store statement: " + STORE_STATEMENT);
        }
        return connection.prepareStatement(STORE_STATEMENT);
    }

    @Override
    protected String buildRetrieveByIdQuery() {
        return RETRIEVE_BY_ID_QUERY;
    }

    @Override
    protected String buildRetrieveQuery(Instant startTime, int numRecords, RetrievalDirection direction, EventDataFilter filter) {
        StringBuilder query = new StringBuilder("SELECT * FROM EVENT_DATA_TABLE WHERE ");
        // add time info
        if(direction == RetrievalDirection.TO_FUTURE) {
            query.append("GenerationTime >= '").append(toTimestamp(startTime).toString()).append("' ");
        } else {
            query.append("GenerationTime <= '").append(toTimestamp(startTime).toString()).append("' ");
        }
        // process filter
        if(filter != null && !filter.isClear()) {
            if(filter.getParentPath() != null) {
                query.append("AND Path LIKE '").append(filter.getParentPath().asString()).append("%' ");
            }
            if(filter.getRouteList() != null && !filter.getRouteList().isEmpty()) {
                query.append("AND Route IN (").append(toFilterListString(filter.getRouteList(), o -> o, "'")).append(") ");
            }
            if(filter.getTypeList() != null && !filter.getTypeList().isEmpty()) {
                query.append("AND Type IN (").append(toFilterListString(filter.getTypeList(), o -> o, "'")).append(") ");
            }
            if(filter.getSourceList() != null && !filter.getSourceList().isEmpty()) {
                query.append("AND Source IN (").append(toFilterListString(filter.getSourceList(), o -> o, "'")).append(") ");
            }
            if(filter.getSeverityList() != null && !filter.getSeverityList().isEmpty()) {
                query.append("AND Severity IN (").append(toEnumFilterListString(filter.getSeverityList())).append(") ");
            }
        }
        // order by and limit
        if(direction == RetrievalDirection.TO_FUTURE) {
            query.append("ORDER BY GenerationTime ASC, UniqueId ASC FETCH NEXT ").append(numRecords).append(" ROWS ONLY");
        } else {
            query.append("ORDER BY GenerationTime DESC, UniqueId DESC FETCH NEXT ").append(numRecords).append(" ROWS ONLY");
        }
        return query.toString();
    }

    @Override
    protected EventData mapToItem(ResultSet rs, EventDataFilter usedFilter) throws SQLException, IOException, ClassNotFoundException {
        long uniqueId = rs.getLong(1);
        Timestamp genTime = rs.getTimestamp(2);
        int externalId = rs.getInt(3);
        String name = rs.getString(4);
        String path = rs.getString(5);
        String qualifier = rs.getString(6);
        Timestamp receptionTime = rs.getTimestamp(7);
        String type = rs.getString(8);
        String route = rs.getString(9);
        String source = rs.getString(10);
        Severity severity = Severity.values()[rs.getShort(11)];
        Object[] additionalDataArray = toObjectArray(rs.getBlob(12));

        return new EventData(new LongUniqueId(uniqueId), toInstant(genTime), externalId, name, SystemEntityPath.fromString(path), qualifier, type, route, source, severity, toInstant(receptionTime), additionalDataArray);
    }

    @Override
    protected String getLastIdQuery() {
        return LAST_ID_QUERY;
    }

    @Override
    public String toString() {
        return "Event Data Archive";
    }
}