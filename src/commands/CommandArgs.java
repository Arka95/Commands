// Copyright (c) 2012-2013 Cloudera, Inc. All rights reserved.
package commands;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Objects;

import java.time.Instant;
import java.util.List;

/**
 * Arguments supplied to a command. Note that all CommandArgs should support JSON
 * serialization through jackson, since they may be stored throughout stages of
 * command execution. Fields of standard JDK types are automatically supported.
 * Custom types, however, have either have proper annotation or have JsonIgnore
 */
//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
//@JsonAutoDetect(getterVisibility = Visibility.NONE, fieldVisibility = Visibility.ANY)
public abstract class CommandArgs {
  private Long scheduleId;
  private String scheduleName;
  private Instant scheduledTime;

  /**
   * List of string arguments.
   *
   * TODO: Field is public for legacy reasons. Add a setter and make private in
   * future commit.
   */
  public List<String> args;

  public final List<String> getArgs() {
    return args;
  }

  /**
   * The String representation of the arguments. Required for event publishing.
   * <p/>
   * Note that any subclass wanting to override toString should instead
   * override {@link #toStringHelper()}.
   */
  public final String toString() {
    ToStringHelper helper = toStringHelper();
    if (args != null) {
      helper.add("args", args);
    }
    if (scheduleId != null) {
      helper.add("scheduleId", scheduleId).add("scheduledTime", scheduledTime);
    }
    if (scheduleName != null) {
      helper.add("scheduleName", scheduleName);
    }
    return helper.toString();
  }

  /**
   * Subclasses looking to override {@link #toString()} should instead override
   * this method.
   */
  protected abstract ToStringHelper toStringHelper();

  @Override
  public int hashCode() {
    return Objects.hashCode(args, scheduleId, scheduleName,
        scheduledTime);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null ||
        obj.getClass() != this.getClass()) {
      return false;
    }
    CommandArgs that = (CommandArgs) obj;
    return Objects.equal(this.args, that.args) &&
        Objects.equal(this.scheduleId, that.scheduleId) &&
        Objects.equal(this.scheduleName, that.scheduleName) &&
        Objects.equal(this.scheduledTime, that.scheduledTime);
  }

  public String toJsonStr() {
    return this.toString();
  }

  /**
   * Id of the associated schedule for scheduled commands and 'null'
   * for unscheduled commands.
   * <p/>
   * This property is filled in by the scheduler for scheduled commands.
   */
  public Long getScheduleId() {
    return scheduleId;
  }
  public void setScheduleId(Long scheduleId) {
    this.scheduleId = scheduleId;
  }

  /**
   * Name of the associated schedule for scheduled commands
   * <p/>
   * This property is filled in by the scheduler for scheduled commands.
   */
  public String getScheduleName() {
    return scheduleName;
  }
  public void setScheduleName(String scheduleName) {
    this.scheduleName = scheduleName;
  }

  /**
   * Time at which a command was scheduled to run. 'null' for unscheduled
   * commands. 
   * <p/>
   * This property is filled in by the scheduler for scheduled commands.
   */
  public Instant getScheduledTime() {
    return scheduledTime;
  }
  public void setScheduledTime(Instant scheduledTime) {
    this.scheduledTime = scheduledTime;
  }

  /**
   * Persists this CommandArgs instance to the CM database and also updates it in the job scheduler.
   
  public void persist() {
    ScmDAOFactory.getSingleton()
                 .newReplicationManager()
                 .updateCommandArgsInJobAndSchedule(getScheduleId(), this);
  }
   */
}
