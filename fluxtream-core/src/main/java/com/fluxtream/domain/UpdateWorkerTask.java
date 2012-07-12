package com.fluxtream.domain;

import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import com.fluxtream.connectors.updaters.UpdateInfo;

@Entity(name="ScheduledUpdate")
@NamedQueries ( {
	@NamedQuery(name = "updateWorkerTasks.delete.all",
			query = "DELETE FROM ScheduledUpdate updt WHERE updt.guestId=?"),
	@NamedQuery(name = "updateWorkerTasks.delete.byApi",
			query = "DELETE FROM ScheduledUpdate updt WHERE updt.guestId=? AND updt.connectorName=?"),
	@NamedQuery(name = "updateWorkerTasks.delete.byApiAndObjectType",
			query = "DELETE FROM ScheduledUpdate updt WHERE updt.guestId=? AND updt.connectorName=? AND updt.objectTypes=?"),
	@NamedQuery(name = "updateWorkerTasks.delete.byStatus",
			query = "DELETE FROM ScheduledUpdate updt WHERE updt.status=?"),
	@NamedQuery( name="updateWorkerTasks.byStatus",
			query="SELECT updt FROM ScheduledUpdate updt WHERE updt.status=? AND updt.timeScheduled<?"),
    @NamedQuery( name="updateWorkerTasks.isScheduled",
                 query="SELECT updt FROM ScheduledUpdate updt WHERE (updt.status=? OR updt.status=?) AND updt.guestId=? " +
                       "AND updt.connectorName=?"),
    @NamedQuery( name="updateWorkerTasks.withObjectTypes.isScheduled",
		query="SELECT updt FROM ScheduledUpdate updt WHERE (updt.status=? OR updt.status=?) AND updt.guestId=? " +
				"AND updt.objectTypes=? AND updt.connectorName=?"),
	@NamedQuery( name="updateWorkerTasks.completed",
		query="SELECT updt FROM ScheduledUpdate updt WHERE updt.status=? " +
				"AND updt.guestId=? " +
				"AND updt.updateType=? AND updt.objectTypes=? " +
				"AND updt.connectorName=?")
})
public class UpdateWorkerTask extends AbstractEntity {

    public static class AuditTrailEntry {
        public AuditTrailEntry() {
        }

        public AuditTrailEntry(final Date date, final String reason, final String nextAction, String stackTrace) {
            this.date = date;
            this.reason = reason;
            this.nextAction = nextAction;
            this.stackTrace = stackTrace;
        }
        public AuditTrailEntry(final Date date, final String reason, final String nextAction) {
            this.date = date;
            this.reason = reason;
            this.nextAction = nextAction;
        }
        public Date date;
        public String reason;
        public String stackTrace;
        public String nextAction;
    }

	public String connectorName;
	public Status status = Status.SCHEDULED;

    @Lob
    public String auditTrail;

	public long timeScheduled;

	public static enum Status { SCHEDULED, IN_PROGRESS, DONE, FAILED, STALLED };
	public UpdateInfo.UpdateType updateType;
	
	public long guestId;
	public int objectTypes;
	public int retries;
	public String jsonParams;
	
	public UpdateWorkerTask() {}
	public UpdateWorkerTask(UpdateWorkerTask other) {
		connectorName = other.connectorName;
		status = other.status;
		timeScheduled = other.timeScheduled;
		updateType = other.updateType;
		guestId = other.guestId;
		objectTypes = other.objectTypes;
		retries = other.retries;
	}

    public void addAuditTrailEntry(AuditTrailEntry auditTrailEntry) {
        if (auditTrail==null) auditTrail = "";
        StringBuilder sb = new StringBuilder(auditTrail);
        sb.append("\\n")
                .append(auditTrailEntry.date.toString())
                .append(" - reason: ")
                .append(auditTrailEntry.reason)
                .append(" - next action: " + auditTrailEntry.nextAction);
        if (auditTrailEntry.stackTrace!=null)
                sb.append("stackTracke: \n" + auditTrailEntry.stackTrace);
        this.auditTrail = sb.toString();
    }
	
	public long getGuestId() { return guestId; }
	public int getObjectTypes() { return objectTypes; }
	public UpdateInfo.UpdateType getUpdateType() { return updateType; }
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(guestId);
		sb.append("/");
		sb.append(connectorName);
		sb.append("/");
		sb.append(objectTypes);
		return sb.toString();
	}
	
	
}