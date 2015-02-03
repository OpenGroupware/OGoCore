/*
  Copyright (C) 2008 Helge Hess

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSTimeRange;
import org.getobjects.foundation.UObject;

/**
 * OGoContactExtValue
 * <p>
 * A structured, extended value for a contact record. The value of the record
 * can be a string, a date or an int. The date is preferred in the derived
 * value.
 * <p>
 * A key can be an 'enumeration', that is, there can be multiple records with
 * the same key.
 * <p>
 * Finally each value can be time restricted (only valid for a specific time
 * span).
 * <p>
 * Note: Person 'emails' are stored in the same database table (company_value),
 *       but they are filtered out in the EOModel.
 *       This is different for other contact types (eg Companies), all
 *       table contents are stored as values in that case.
 * <p>
 * Types:
 * <ul>
 *   <li>1 - string
 *   <li>2 - checkbox
 *   <li>3 - email
 *   <li>9 - ??
 * </ul>
 * 
 * @author helge
 */
public class OGoContactExtValue extends OGoObject
  implements IOGoContactChildObject, IOGoTimeRangeObject
{
  protected Number  companyId;
  protected String  key;
  
  protected String  vString;
  protected Date    vDate;
  protected Number  vInt;
  protected boolean isEnum;
  
  protected Date    startDate;
  protected Date    endDate;
  
  public OGoContactExtValue(final EOEntity _entity) {
    super(_entity);
  }
    
  /* accessors */
  
  public void setCompanyId(final Number _id) {
    this.companyId = _id;
  }
  public Number companyId() {
    return this.companyId;
  }
  
  public void setKey(final String _key) {
    this.key = _key;
  }
  public String key() {
    return this.key;
  }
  
  public void setIsEnum(final Object _v) {
    this.isEnum = UObject.boolValue(_v);
  }
  public Object isEnum() {
    return this.isEnum;
  }
  public boolean isMultiValue() {
    return UObject.boolValue(this.isEnum());
  }
  
  public void setVString(final String _value) {
    this.vString = _value;
  }
  public String vString() {
    return this.vString;
  }
  
  public void setVDate(final Object _value) {
    this.vDate = UObject.dateValue(_value);
  }
  public Object vDate() {
    return this.vDate;
  }
  
  public void setVInt(final Number _value) {
    this.vInt = _value;
  }
  public Number vInt() {
    return this.vInt;
  }
  
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
  
  
  /* value */
  
  public void setValue(final Object _value) {
    String s = null;
    Date   d = null;
    Number i = null;
    
    if (_value == null)
      ;
    else if (_value instanceof String) {
      s = (String)_value;
      
      try {
        i = Integer.parseInt(s);
      }
      catch (NumberFormatException e) {
        i = null;
      }
    }
    else if (_value instanceof Number) {
      s = _value.toString();
      i = (Number)_value;
    }
    else if (_value instanceof Date)
      d = (Date)_value;
    else {
      s = _value.toString();
      i = UObject.intValue(_value);
    }

    /* apply */
    this.setVString(s);
    this.setVDate(d);
    this.setVInt(i);
  }
  
  public Object value() {
    if (this.vDate   != null) return this.vDate;
    if (this.vString != null) return this.vString;
    if (this.vInt    != null) return this.vInt;
    return null;
  }

  public String stringValue() {
    if (this.vString != null) return this.vString;
    if (this.vDate   != null) return this.vDate.toString();
    if (this.vInt    != null) return this.vInt.toString();
    return null;
  }
  
  public int intValue() {
    if (this.vInt != null) return this.vInt.intValue();
    return UObject.intValue(this.vString);
  }
  
  @Override
  public boolean isEmpty() { /* only the value counts */
    if (this.vDate   != null) return false;
    if (this.vInt    != null) return false;
    if (this.vString != null) return UObject.isEmpty(this.vString);
    return true;
  }

  
  /* time ranges */
  
  public boolean isTimeRestricted() {
    if (this.startDate != null) return true;
    if (this.endDate   != null) return true;
    return false;
  }
  
  public void setTimeRange(final NSTimeRange _range) {
    this.setStartDate(_range != null ? _range.fromDate() : null);
    this.setEndDate  (_range != null ? _range.toDate()   : null);
  }
  public NSTimeRange timeRange() {
    final Date sd = (Date)this.startDate();
    final Date ed = (Date)this.endDate();
    if (sd == null && ed == null) return null;
    if (this.startDate == null && this.endDate == null) return null;
    return new NSTimeRange(this.startDate, this.endDate);
  }
  
  public boolean isInTimeRange(NSTimeRange _range) {
    if (_range == null)
      return false;
    
    NSTimeRange r = this.timeRange();
    if (r == null)
      return true; /* no restriction, valid in any range */
    
    return r.overlaps(_range);
  }
  
  /**
   * Returns whether the objects time constraints match the given date. If the
   * object has no timerange, it always returns true.
   *  
   * @return true if the _date is in the objects timerange
   */
  public boolean isActiveAtDate(final Date _date) {
    if (this.startDate == null && this.endDate == null)
      return true;
    
    NSTimeRange r = this.timeRange();
    return r != null ? r.containsDate(_date) : null;
  }
  /**
   * Returns whether the objects time constraints match the current time. If the
   * object has no timerange, it always returns true.
   *  
   * @return true if the _date is in the objects timerange
   */
  public boolean isCurrent() {
    if (this.startDate == null && this.endDate == null)
      return true;
    
    return this.isActiveAtDate(new Date());
  }
  
  /**
   * Returns the value of the extended attribute if the attributes timerange
   * is currently active. Otherwise it returns null.
   * 
   * @return the value or null
   */
  public Object valueIfCurrent() {
    return this.isCurrent() ? this.value() : null;
  }
  
  
  /* grouping */
  
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getValueMap
    (Collection<OGoContactExtValue> _valueRecords, NSTimeRange _timeRange)
  {
    if (_valueRecords == null || _valueRecords.size() == 0)
      return null;
    
    Map<String, Object> map = new HashMap<String, Object>(_valueRecords.size());
    for (OGoContactExtValue record: _valueRecords) {
      if (_timeRange != null) {
        NSTimeRange r = record.timeRange();
        if (r != null && !_timeRange.overlaps(r))
          continue; /* out of time range */
      }
      
      String k = record.key();
      if (record.isMultiValue()) {
        List<Object> v = (List<Object>)map.get(k);
        if (v == null) {
          v = new ArrayList<Object>(8);
          map.put(k, v);
        }
        v.add(record.value());
      }
      else {
        map.put(k, record.value());
      }
    }
    
    return map;
  }
}
