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

import java.util.Calendar;
import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSTimeRange;
import org.getobjects.foundation.UObject;

/**
 * OGoEvent
 * <p>
 * An event, eg an appointment or meeting. Would be represented as a VEVENT
 * in iCalendar.
 * 
 * <p>
 * @author helge
 */
public class OGoEvent extends OGoCalObject
  implements IOGoTimeRangeObject, Comparable<OGoEvent>
{
  
  protected Date startDate;
  protected Date endDate;
  
  public String title;
  
  public Number cycleEventId; /* "master" event */
  public String cycleRule;
  public Date   cycleUntil;

  public OGoEvent(EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setStartDate(final Object _date) {
    this.startDate = UObject.dateValue(_date);
  }
  public Object startDate() {
    return this.startDate;
  }
  
  public void setEndDate(final Object _date) {
    this.endDate = UObject.dateValue(_date);
  }
  public Object endDate() {
    return this.endDate;
  }
  
  
  /* Calendaring */
  
  public void setTimeRange(final NSTimeRange _range) {
    this.setStartDate(_range != null ? _range.fromDate() : null);
    this.setEndDate  (_range != null ? _range.toDate()   : null);
  }
  public NSTimeRange timeRange() {
    final Date sd = (Date)this.startDate();
    final Date ed = (Date)this.endDate();
    if (sd == null && ed == null) return null;
    if (sd == null || ed == null) return null; // no open ends/starts?
    return new NSTimeRange(sd.getTime(), ed.getTime());
  }
  
  public void setStartAsCalendar(final Calendar _date) {
    this.setStartDate(_date != null ? _date.getTime() : null);
  }
  public Calendar startAsCalendar() {
    Calendar cal = Calendar.getInstance();
    cal.setTime((Date)this.startDate());
    return cal;
  }
  
  public void setEndAsCalendar(final Calendar _date) {
    this.setEndDate(_date != null ? _date.getTime() : null);
  }
  public Calendar endAsCalendar() {
    Calendar cal = Calendar.getInstance();
    cal.setTime((Date)this.endDate());
    return cal;
  }


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Date";
  }

  
  /* compare */
  
  public int compareTo(final OGoEvent _other) {
    if (_other == this) return 0;
    if (_other == null) return 1;
    
    Calendar mn = this.startAsCalendar();
    Calendar on = _other.endAsCalendar();
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    
    return mn.compareTo(on);
  }
}
