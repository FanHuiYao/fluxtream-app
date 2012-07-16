package com.fluxtream.connectors.updaters;

import static com.fluxtream.utils.Utils.stackTrace;

import java.util.Vector;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.fluxtream.connectors.ApiClientSupport;
import com.fluxtream.connectors.Connector;
import com.fluxtream.connectors.dao.FacetDao;
import com.fluxtream.connectors.updaters.UpdateInfo.UpdateType;
import com.fluxtream.domain.AbstractUserProfile;
import com.fluxtream.services.ApiDataService;
import com.fluxtream.services.BodyTrackStorageService;
import com.fluxtream.services.ConnectorUpdateService;
import com.fluxtream.services.GuestService;
import com.fluxtream.services.JPADaoService;
import com.fluxtream.services.NotificationsService;
import com.fluxtream.utils.Utils;
import org.springframework.beans.factory.annotation.Qualifier;

public abstract class AbstractUpdater extends ApiClientSupport {

	static Logger logger = Logger.getLogger(AbstractUpdater.class);

    static final int RETRY_ATTEMPTS = 3;

    @Qualifier("apiDataServiceImpl")
    @Autowired
	protected ApiDataService apiDataService;

	@Autowired
	protected GuestService guestService;

	@Autowired
	protected JPADaoService jpaDaoService;

    @Qualifier("JPAFacetDao")
    @Autowired
	protected FacetDao facetDao;

    @Qualifier("notificationsServiceImpl")
    @Autowired
	protected NotificationsService notificationsService;

    @Qualifier("bodyTrackStorageServiceImpl")
    @Autowired
	protected BodyTrackStorageService bodyTrackStorageService;

	private static Vector<RunningUpdate> runningUpdates = new Vector<RunningUpdate>();

	private String connectorName;

	final protected Connector connector() {
		if (connectorName == null)
			connectorName = getConnectorName(this.getClass().getName());
		return Connector.getConnector(connectorName);
	}

	private String getConnectorName(String beanClassName) {
		int startIndex = "com.fluxtream.connectors.".length();
        return beanClassName.substring(startIndex,
                beanClassName.indexOf(".", startIndex + 1));
	}

	public AbstractUpdater() {
	}

	@Autowired
	final protected void setConnectorUpdateService(@Qualifier("connectorUpdateServiceImpl") ConnectorUpdateService ads) {
		Connector connector = connector();
		ads.addUpdater(connector, this);
	}

	// TODO: this is not clusterizable -> should be done with redis
	private class RunningUpdate {
		Connector api;
		int objectTypes;
		UpdateInfo updateInfo;
		long guestId;

		public RunningUpdate(Connector api, int objectTypes,
				UpdateInfo updateInfo, long guestId) {
			super();
			this.api = api;
			this.objectTypes = objectTypes;
			this.updateInfo = updateInfo;
			this.guestId = guestId;
		}

		public boolean equals(Object o) {
            if(o instanceof  RunningUpdate)
            {
                RunningUpdate ru = (RunningUpdate) o;
                return ru.api == api && ru.objectTypes == objectTypes
                        && ru.updateInfo.isIdentical(updateInfo)
                        && ru.guestId == guestId;
            }
            else
                return false;
		}

	}

	public final UpdateResult updateDataHistory(UpdateInfo updateInfo)
			throws Exception {

        String stackTrace = null;
        for(int i=0; i<RETRY_ATTEMPTS; i++)
        {
            try {
                updateConnectorDataHistory(updateInfo);
                bodyTrackStorageService.storeInitialHistory(
                        updateInfo.getGuestId(), updateInfo.apiKey.getConnector()
                                .getName());

                return UpdateResult.successResult();
            } catch (RateLimitReachedException e) {
                logger.info("guestId="
                        + updateInfo.apiKey.getGuestId()
                        + " action=bg_update stage=return_results result=rateLimitReached");
                return UpdateResult.rateLimitReachedResult();
            } catch (Throwable t) {
                stackTrace = stackTrace(t);
                logger.info("guestId=" + updateInfo.apiKey.getGuestId()
                        + " action=bg_update stage=return_results result=failed \n"
                        + stackTrace);
            }
        }
        return UpdateResult.failedResult(stackTrace);

		// String message = "Your " + connector().prettyName() +
		// " history has been imported";
		// if
		// (updateInfo.objectTypes()!=null&&updateInfo.objectTypes().size()>0)
		// message = "Your " + connector().prettyName() + " (" +
		// objectTypesString(updateInfo.objectTypes()) +
		// ") history has been imported";
		// notificationsService.addNotification(updateInfo.apiKey.getGuestId(),
		// Notification.Type.INFO, message);
	}

	// private String objectTypesString(List<ObjectType> list) {
	// StringBuffer result = new StringBuffer();
	// for (int i=0; i<list.size(); i++) {
	// if (i>0) result.append(", ");
	// result.append(list.get(i).prettyname());
	// }
	// return result.toString();
	// }

	@SuppressWarnings({"unchecked","unused"})
	protected final <T extends AbstractUserProfile> T saveUserProfile(
			UpdateInfo updateInfo, Class<T> clazz) throws Exception {
		AbstractUserProfile loadUserProfile = loadUserProfile(updateInfo, clazz);
		guestService.saveUserProfile(updateInfo.apiKey.getGuestId(),
				loadUserProfile);
		return (T) loadUserProfile;
	}

    @SuppressWarnings("unused")
	protected <T extends AbstractUserProfile> T loadUserProfile(
			UpdateInfo updateInfo, Class<T> clazz) throws Exception {
		throw new RuntimeException("Not Implemented");
	}

    /**
     * Updates all connector information
     * @param updateInfo update information for the connector
     * @throws Exception If an api's limit has been reached or if an update fails for another reason
     */
	protected void updateConnectorDataHistory(UpdateInfo updateInfo)
			throws Exception {
		throw new RuntimeException("Not Implemented");
	}

	public final UpdateResult updateData(UpdateInfo updateInfo) {
		if (hasReachedRateLimit(connector(), updateInfo.apiKey.getGuestId())) {
			logger.warn("rate limit was reached: connector: "
					+ updateInfo.apiKey.getConnector().toString() + ", guest: "
					+ updateInfo.apiKey.getGuestId());
			return new UpdateResult(
					UpdateResult.ResultType.HAS_REACHED_RATE_LIMIT);
		}

		// prevent two equivalent updates of running at the same time
		RunningUpdate runningUpdate = new RunningUpdate(
				updateInfo.apiKey.getConnector(), updateInfo.objectTypes,
				updateInfo, updateInfo.apiKey.getGuestId());

		if (runningUpdates.contains(runningUpdate))
			return new UpdateResult(UpdateResult.ResultType.DUPLICATE_UPDATE);
		runningUpdates.add(runningUpdate);

		UpdateResult updateResult = new UpdateResult();
		try {
			if (updateInfo.getUpdateType() == UpdateType.TIME_INTERVAL_UPDATE)
				apiDataService.eraseApiData(updateInfo.apiKey.getGuestId(),
						connector(), updateInfo.objectTypes,
						updateInfo.getTimeInterval());
            for(int i = 0; i<RETRY_ATTEMPTS; i++){
                try {
                    updateConnectorData(updateInfo);
                    updateResult.type = UpdateResult.ResultType.UPDATE_SUCCEEDED;
                    break;
                } catch (Exception e) {
                    updateResult = new UpdateResult(
                            UpdateResult.ResultType.UPDATE_FAILED);
                    updateResult.stackTrace = Utils.stackTrace(e);
                    e.printStackTrace();
                }
            }
		} finally {
			runningUpdates.remove(runningUpdate);
		}
		return updateResult;
	}

	final protected void countSuccessfulApiCall(long guestId, int objectTypes,
			long then, String query) {
		connectorUpdateService.addApiUpdate(guestId, connector(), objectTypes,
				then, System.currentTimeMillis() - then, query, true);
	}

	final protected void countFailedApiCall(long guestId, int objectTypes,
			long then, String query) {
		connectorUpdateService.addApiUpdate(guestId, connector(), objectTypes,
				then, System.currentTimeMillis() - then, query, false);
	}

    /**
     * Performs and incremental update of the connector
     * @param updateInfo Update information
     * @throws Exception If update fails
     */
	protected abstract void updateConnectorData(UpdateInfo updateInfo)
			throws Exception;

}
