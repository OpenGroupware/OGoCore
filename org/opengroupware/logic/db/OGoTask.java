/*
  Copyright (C) 2007-2008 Helge Hess

  This file is part of OpenGroupware.org (OGo)

  OGo is free software; you can redistribute it and/or modify it under
  the terms of the GNU General Public License as published by the
  Free Software Foundation; either version 2, or (at your option) any
  later version.

  OGo is distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU General Public
  License along with OGo; see the file COPYING.  If not, write to the
  Free Software Foundation, 59 Temple Place - Suite 330, Boston, MA
  02111-1307, USA.
*/
package org.opengroupware.logic.db;

import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOKey;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.foundation.NSJavaRuntime;

/**
 * Represents a task in the OGo database.
 * <p>
 * 
 * Possible Status values:
 * <ul>
 *   <li>00_created
 *   <li>02_rejected
 *   <li>20_processing
 *   <li>25_done
 *   <li>30_archived
 * </ul>
 * 
 * 
 * 
 * @author helge
 */
public class OGoTask extends OGoCalObject {
  public static final String STATUS_CREATED    = "00_created";
  public static final String STATUS_REJECTED   = "02_rejected";
  public static final String STATUS_PROCESSING = "20_processing";
  public static final String STATUS_DONE       = "25_done";
  public static final String STATUS_ARCHIVED   = "30_archived";
  
  public Number  ownerId;
  public Number  creatorId;
  public Boolean isOwnerTeam;
  public String  status;
  public Date    startDate;
  public Date    endDate;
  public Date    completionDate;

  public OGoTask(EOEntity _entity) {
    super(_entity);
  }
  
  /* methods */

  /**
   * Check whether a task was completed. A task is considered done when its
   * status is '25_done' or if its status is '30_archived' and a completion date
   * or +100% completion percentage was set.
   * 
   * @return true if the task is considered done
   */
  public boolean isDone() {
    String status = (String)this.valueForKey("status");
    if (status == null) return false;
    
    if (status.equals(STATUS_DONE))
      return true;
    
    if (status.equals(STATUS_ARCHIVED)) {
      /* This does not necessarily imply that the task was completed! We check
       * by looking for a 'completionDate' or 'percentComplete' */
      if (this.valueForKey("completionDate") != null)
        return true;
      
      int completion = NSJavaRuntime.intValueForKey(this, "percentComplete");
      if (completion >= 100) /* allow for overcompletion ;-) */
        return true;
    }
    
    return false;
  }

  /**
   * Convenience method which checks whether the status is '30_archived'.
   * 
   * @return true if the task has the archived status
   */
  public boolean isArchived() {
    String status = (String)this.valueForKey("status");
    if (status == null) return false;

    return status.equals(STATUS_ARCHIVED);
  }
  
  /**
   * Check whether the endDate of a task is before _now. Archived or done tasks
   * are not considered. If the task has no endDate, its not considered either.
   * 
   * @param the reference date (if NULL, new Date() will be used)
   * @return true if the task is overdue, false otherwise
   */
  public boolean isOverdue(final Date _now) {
    String status = (String)this.valueForKey("status");
    if (status == null) return false;
    
    if (status.equals(STATUS_DONE))     return false;
    if (status.equals(STATUS_ARCHIVED)) return false;
    
    Date dueDate = (Date)this.valueForKey("endDate");
    if (dueDate == null)
      return false; /* task has no enddate */
    
    return dueDate.before(_now != null ? _now : new Date());
  }
  
  protected static EOQualifier isOverdueQualifier =
    EOQualifier.parse("status != %@ AND status != %@ AND "+
        "endDate IS NOT NULL AND endDate < $now", STATUS_DONE, STATUS_ARCHIVED);
    
  public static EOQualifier isOverdueQualifier(Date _now) {
    if (_now == null) _now = new Date();
    return isOverdueQualifier.qualifierWithBindings("now", _now);
  }
  
  /**
   * This calls isOverdue with new Date(). Its preferable to create the current
   * time object once in the controller code and pass it to the backend to avoid
   * inconsistencies in a single transaction!
   * 
   * @see isOverdue(Date);
   * @return true if the task is overdue, false otherwise
   */
  public boolean isOverdue() {
    return this.isOverdue(new Date() /* now */);
  }
  
  
  /* dates */
  
  /**
   * This returns the `endDate` of the task, unless the task is done, then the
   * `completionDate` is returned (if set).
   */
  public Date primaryEndDate() {
    if (!this.isDone())
      return (Date)this.valueForKey("endDate");

    final Date date = (Date)this.valueForKey("completionDate");
    if (date != null)
      return date;
    
    return (Date)this.valueForKey("endDate");
  }
  
  
  /* timer */
  
  /**
   * Returns true if the task has an associated timer, which is running. A
   * timer can be used by the user to track time he is spending on a task.
   * This is NOT a per user field.
   * 
   * @return true if a timer was started for the task
   */
  public boolean isTimerRunning() {
    return this.valueForKey("timerStartDate") != null;
  }
  
  /**
   * Starts the task timer by saving the current date into the 'timerStartDate'
   * field. 
   */
  public void startTimer() {
    // need to save the object afterwards! ;-)
    this.takeValueForKey(new Date(), "timerStartDate");
  }
  
  /**
   * Stops a running task timer. The time is added to the 'actualWorkInMinutes'
   * field of the task.
   */
  public void stopTimer() {
    // need to save the object afterwards! ;-)
    Date startDate = (Date)this.valueForKey("timerStartDate");
    if (startDate == null)
      return;
    
    int work = NSJavaRuntime.intValueForKey(this, "actualWorkInMinutes");
    
    long timeElapsed = new Date().getTime() - startDate.getTime();
    if (timeElapsed < 5000 /* 5s */) {
      /* some really quick start/stop, we disregard that for calculation */
    }
    else {
      timeElapsed = timeElapsed / 1000 / 60; /* convert to minutes */
      
      if (timeElapsed < 1) /* we capture at least a minute */
        timeElapsed = 1;
      work += timeElapsed;
      this.takeValueForKey(work, "actualWorkInMinutes");
    }

    this.takeValueForKey(null, "timerStartDate");
  }
  
  
  /* owner */
  
  /**
   * Returns the owner of the task, can be an OGoPerson or a OGoTeam. This
   * only works if the relationships (ownerTeam/ownerPerson) are fetched.
   * 
   * @return an OGoContact which owns this task
   */
  public OGoContact owner() {
    return (OGoContact)this.valueForKey
      (NSJavaRuntime.boolValueForKey(this, "isOwnerTeam")
       ? "ownerTeam" : "ownerPerson"); 
  }


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Job";
  }
  
  
  /* keys */
  
  public static final EOKey<Number>  keyOwnerId =
    new EOKey<Number>("ownerId");
  public static final EOKey<Number>  keyCreatorId =
    new EOKey<Number>("creatorId");
  public static final EOKey<Boolean> keyIsOwnerTeam =
    new EOKey<Boolean>("isOwnerTeam");
  public static final EOKey<String>  keyStatus =
    new EOKey<String>("status");
  public static final EOKey<Date>    keyStartDate =
    new EOKey<Date>("startDate");
  public static final EOKey<Date>    keyEndDate =
    new EOKey<Date>("endDate");
  public static final EOKey<Date>    keyCompletionDate =
    new EOKey<Date>("completionDate");
}
