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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOAndQualifier;
import org.getobjects.eocontrol.EOKey;
import org.getobjects.eocontrol.EOKeyValueQualifier;
import org.getobjects.eocontrol.EONotQualifier;
import org.getobjects.eocontrol.EOOrQualifier;
import org.getobjects.eocontrol.EOQualifier;
import org.getobjects.eocontrol.EOQualifierEvaluation;
import org.getobjects.foundation.NSTimeRange;
import org.getobjects.foundation.UObject;

/**
 * OGoPerson
 * <p>
 * Represents a person contact in the OGo database. 'person' is a view on
 * the 'company' table.
 * 
 * <p>
 * Per default an OGo person has those phone numbers types:
 * <ul>
 *   <li>01_tel
 *   <li>02_tel
 *   <li>03_tel_funk
 *   <li>05_tel_private
 *   <li>10_fax
 *   <li>15_fax_private
 * </ul>
 * And those address types:
 * <ul>
 *   <li>private
 *   <li>mailing
 *   <li>location
 * </ul>
 * 
 * <p>
 * Example salutations ('salutation' property):
 * <ul>
 *   <li>03_dear_mr
 *   <li>06_geehrt_herr
 *   <li>07_geehrt_frau
 *   <li>08_geehrt_herr_prof
 *   <li>10_geehrt_frau_prof
 * </ul>
 * 
 * <p>
 * Example degrees ('degree' property):
 * <ul>
 *   <li>
 *   <li>Dr.
 *   <li>Dr.Dr.
 *   <li>OPhR
 *   <li>OPhR Dr.
 *   <li>PhR
 *   <li>Prof.
 *   <li>Prof.Dr.
 * </ul>
 * 
 * <p>
 * @author helge
 */
public class OGoPerson extends OGoContact implements Comparable<OGoPerson>{
  
  public    String firstname;
  public    String lastname;
  public    String middlename;
  public    String nickname;
  protected Date   birthday;
  protected Date   dayofdeath;
  
  public Collection<OGoEMailAddress> emails;

  public OGoPerson(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setIsPerson(final boolean _flag) {
    if (!_flag)
      log().error("attempt to remove isPerson flag: " + this);
  }
  public boolean isPerson() { // TBD: object result to make it balanced?
    return true;
  }
  
  public void setBirthday(final Object _v) {
    if (_v instanceof Date)
      this.birthday = (Date)_v;
    else if (_v instanceof Calendar)
      this.birthday = ((Calendar)_v).getTime();
    else if (_v == null || UObject.isEmpty(_v))
      this.birthday = null;
    else
      log.error("attempt to set invalid value for birthday key: " + _v);
  }
  public Object birthday() {
    return this.birthday;
  }
  
  public void setDayofdeath(final Object _v) {
    if (_v instanceof Date)
      this.dayofdeath = (Date)_v;
    else if (_v instanceof Calendar)
      this.dayofdeath = ((Calendar)_v).getTime();
    else if (_v == null || UObject.isEmpty(_v))
      this.dayofdeath = null;
    else
      log.error("attempt to set invalid value for dayofdeath key: " + _v);
  }
  public Object dayofdeath() {
    return this.dayofdeath;
  }
  
  
  /* names */
  
  // TBD: add a clever setName method! :-)
  
  /**
   * Convenience method. Returns firstname plus lastname.
   * Sample:<pre>
   *   Donald Duck</pre>
   * 
   * @return the name of the person (or null if none is set)
   */
  public String name() {
    final StringBuilder sb = new StringBuilder(64);
    
    if (this.firstname != null)
      sb.append(this.firstname.trim());
    if (this.lastname != null) {
      String s = this.lastname.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    
    return sb.length() > 0 ? sb.toString() : null;
  }
  
  /**
   * Convenience method. Returns degree plus firstname plus lastname.
   * Sample:<pre>
   *   Prof. Dr. Henry Hess</pre>
   * 
   * @return the name of the person (or null if none is set)
   */
  public String nameWithDegree() {
    final StringBuilder sb = new StringBuilder(64);
    String s;
    
    if ((s = (String)this.valueForKey("degree")) != null) {
      s = s.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    if (this.firstname != null) {
      s = this.firstname.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    if (this.lastname != null) {
      s = this.lastname.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    
    return sb.length() > 0 ? sb.toString() : null;
  }
  
  /**
   * Convenience method. Returns:
   *   nameTitle + firstname + middlename + lastname + nameAffix
   * 
   * @return the name of the person (or null if none is set)
   */
  public String fullname() {
    // also: degree, salutation
    final StringBuilder sb = new StringBuilder(64);
    String s;
    
    if ((s = (String)this.valueForKey("nameTitle")) != null) {
      s = s.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    if (this.firstname != null) {
      s = this.firstname.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    if (this.middlename != null) {
      s = this.middlename.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    if (this.lastname != null) {
      s = this.lastname.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    if ((s = (String)this.valueForKey("nameAffix")) != null) {
      s = s.trim();
      if (sb.length() > 0 && s.length() > 0) sb.append(" ");
      sb.append(s);
    }
    
    return sb.length() > 0 ? sb.toString() : null;
  }
  
  /**
   * Convenience method. Returns lastname comma firstname.
   * Sample:<pre>
   *   Duck, Donald</pre>
   * 
   * @return the name of the person (or null if none is set)
   */
  public String reverseName() {
    final StringBuilder sb = new StringBuilder(64);
    
    if (this.lastname != null)
      sb.append(this.lastname.trim());
    
    if (this.firstname != null) {
      String s = this.firstname.trim();
      if (sb.length() > 0 && s.length() > 0)
        sb.append(", ");
      else
        sb.append("-, ");
      sb.append(s);
    }
    
    return sb.length() > 0 ? sb.toString() : null;
  }
  
  
  /* emails */

  /**
   * Locates the preferred email address for the person contact. This can be
   * the address which is explicitly marked preferred (using the vCard PREF
   * feature).
   * If no address is marked preferred, this takes 'email1' or the first address
   * found.
   * <p>
   * Note: The emails relationship must be prefetched so that this works
   * <p>
   * Note: This is different to OGoCompany. OGoCompany has the preferred email
   *       address in the 'email' attribute of the main table.
   *       OGoPerson stores an arbitrary number of emails in the company_value
   *       table.
   * 
   * @return an OGoEMailAddress representing the preferred address
   */
  public OGoEMailAddress preferredEMailAddress() {
    if (this.emails == null || this.emails.size() == 0)
      return null;
    
    // Hm. Is the isNotEmpty check OK? It means that this should not be used
    //     for 'editing the preferred address'.
    OGoEMailAddress first  = null;
    OGoEMailAddress email1 = null;
    for (OGoEMailAddress email: this.emails) {
      if (email.isPreferred())
        // TBD: check for contents?
        return email; /* found an explicitly PREF marked email */

      if ("email1".equals(email.key))
        email1 = email;
      
      if (first == null && email.isNotEmpty())
        first = email;
    }
    
    if (email1 != null && email1.isNotEmpty())
      return email1;
    
    /* just take the first email then (arbitary) */
    return first;
  }
  
  /**
   * Locates the preferred email address for the person contact. This can be
   * the address which is explicitly marked preferred (using the vCard PREF
   * feature).
   * If no address is marked preferred, this takes 'email1' or the first address
   * found.
   * 
   * @see preferredEMailAddress
   * 
   * @return the email address, as a String
   */
  public String preferredEMail() {
    OGoEMailAddress a = this.preferredEMailAddress();
    return a != null ? a.value() : null;
  }
  
  
  /* employments */
  
  public Collection<OGoEmployment> currentEmployments() {
    return this.employmentsAtDate(new Date());
  }
  public Collection<OGoEmployment> previousEmployments() {
    return this.employmentsBeforeDate(new Date());
  }
  
  @SuppressWarnings("unchecked")
  public Collection<OGoEmployment> employmentsAtDate(final Date _date) {
    // you can also do this in the template, eg using:
    //   <wo:for filter="endDate IS NULL OR endDate > $now"
    if (_date == null)
      return (Collection<OGoEmployment>)this.valueForKey("employments");
    
    final EOQualifierEvaluation q = new EOOrQualifier(
        endDateNull,
        new EOKeyValueQualifier("endDate",
            EOQualifier.ComparisonOperation.GREATER_THAN, _date));
    return this.filterRelationship("employments", q);
  }
  @SuppressWarnings("unchecked")
  public Collection<OGoEmployment> employmentsBeforeDate(final Date _date) {
    if (_date == null)
      return (Collection<OGoEmployment>)this.valueForKey("employments");
    
    final EOQualifierEvaluation q = new EOAndQualifier(
        endDateNotNull,
        new EOKeyValueQualifier("endDate",
            EOQualifier.ComparisonOperation.LESS_THAN_OR_EQUAL, _date));
    return this.filterRelationship("employments", q);
  }
  @SuppressWarnings("unchecked")
  public Collection<OGoEmployment> employmentsInTimeRange
    (final NSTimeRange _range)
  {
    final Collection<OGoEmployment> all =
      (Collection<OGoEmployment>)this.valueForKey("employments");
    
    if (_range == null || all == null || all.size() < 1)
      return all;
    
    Collection<OGoEmployment> matchingEmployments = null;
    for (OGoEmployment emp: all) {
      if (emp == null)
        continue;
      
      NSTimeRange tr = emp.timeRange(); // can be null!
      if (tr != null && !tr.overlaps(_range))
        continue;
      
      if (matchingEmployments == null)
        matchingEmployments = new ArrayList<OGoEmployment>(all.size());
      
      matchingEmployments.add(emp);
    }
    
    return matchingEmployments;
  }
  
  /**
   * Convenience method which returns the primary private phone number
   * (aka 05_tel_private).
   * 
   * @return the private phone number of the person
   */
  public OGoPhoneNumber privatePhone() {
    return this.phoneNumberWithKey("05_tel_private");
  }
  /**
   * Convenience method which returns the primary mobile phone number
   * (aka 03_tel_funk).
   * 
   * @return the mobile phone number of the person
   */
  public OGoPhoneNumber mobilePhone() {
    return this.phoneNumberWithKey("03_tel_funk");
  }
  
  
  /* birth and death */
  
  public boolean isBorn() {
    Object v = this.birthday();
    if (v == null)
      return true; /* no birthday recorded, assume the person is born */
    
    if (v instanceof Date) {
      Date now = new Date();
      return ((Date)v).before(now);
    }
    if (v instanceof Calendar) {
      Date now = new Date();
      return ((Calendar)v).getTime().before(now);
    }
    
    log.error("unexpected value in birthday field: " + v);
    return false;
  }
  public boolean didDie() {
    Object v = this.dayofdeath();
    if (v == null)
      return false; /* did not die yet */
    
    if (v instanceof Date) {
      Date now = new Date();
      return ((Date)v).before(now);
    }
    if (v instanceof Calendar) {
      Date now = new Date();
      return ((Calendar)v).getTime().before(now);
    }
    
    log.error("unexpected value in dayofdeath field: " + v);
    return false;
  }
  public boolean isAlive() {
    if (!this.isBorn()) return false;
    return !this.didDie();
  }
  
  /**
   * Returns the age of the person, derived from the birthday and from the
   * day of death, if the person died.
   * 
   * @return the age of the person, or -1 if no birthday is set
   */
  public int age() {
    // TBD: the code needs to be checked for edge conditions (eg birthdays! ;-)
    Calendar scal;
    Calendar ecal;
    Date start; // TBD: remove those
    Date end;
    Object v = this.birthday();
    if (v == null)
      return -1; /* no birthday recorded, can't calculate age */
    
    if (v instanceof Date)
      start = (Date)v;
    else
      start = ((Calendar)v).getTime();
    
    v = this.dayofdeath();
    if (v instanceof Date)
      end = (Date)v;
    else if (v instanceof Calendar)
      end = ((Calendar)v).getTime();
    else
      end = new Date();
    
    if (end.before(start))
      return -1; /* end before start?? */
    
    // TBD: need locale/timezone!? Hm. Not necessarily. Maybe for timezone
    //      switches the calc is wrong - if the timezone definition changed
    //      for the birthday.
    scal = Calendar.getInstance();
    ecal = Calendar.getInstance();
    scal.setTime(start);
    ecal.setTime(end);
    
    /* normalize */
    scal.set(Calendar.HOUR,   0);
    scal.set(Calendar.MINUTE, 0);
    scal.set(Calendar.SECOND, 0);
    ecal.set(Calendar.HOUR,   0);
    ecal.set(Calendar.MINUTE, 0);
    ecal.set(Calendar.SECOND, 0);
    
    /* compare years */
    int syear = scal.get(Calendar.YEAR);
    int eyear = ecal.get(Calendar.YEAR);
    if (syear == eyear) return 0; /* newborn */
    if (syear >  eyear) return -1; /* invalid */
    
    int age = eyear - syear;
    scal.set(Calendar.YEAR, eyear); // move day/month to same year
    if (scal.after(ecal))
      age--; /* did not have birthday yet */
    
    return age;
  }


  /* compare */
  
  public int compareTo(final OGoPerson _other) {
    if (_other == this) return 0;
    if (_other == null) return 1;
    
    String mn = (String)this.valueForKey("lastname");
    String on = (String)_other.valueForKey("lastname");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    int res = mn.compareTo(on);
    if (res != 0) return res;

    mn = (String)this.valueForKey("firstname");
    on = (String)_other.valueForKey("firstname");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    res = mn.compareTo(on);
    if (res != 0) return res;

    mn = (String)this.valueForKey("middlename");
    on = (String)_other.valueForKey("middlename");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    res = mn.compareTo(on);
    if (res != 0) return res;

    mn = (String)this.valueForKey("number");
    on = (String)_other.valueForKey("number");
    if (mn == on)   return 0;
    if (on == null) return 1;
    if (mn == null) return -1;
    return mn.compareTo(on);
  }
  
  
  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Person";
  }
  
  /* misc */
  
  private static final EOQualifier endDateNull =
    new EOKeyValueQualifier("endDate", null);
  private static final EOQualifier endDateNotNull =
    new EONotQualifier(endDateNull);


  /* keys */
  // Not sure whether we actually want to use those. Its a test :-)

  public static final EOKey<String> keyFirstname =
    new EOKey<String>("firstname");
  public static final EOKey<String> keyLastname =
    new EOKey<String>("lastname");
  public static final EOKey<String> keyMiddlename =
    new EOKey<String>("middlename");
  public static final EOKey<String> keyNickname =
    new EOKey<String>("nickname");
  public static final EOKey<Date>   keyBirthday =
    new EOKey<Date>("birthday");
  public static final EOKey<Date>   keyDayofdeath =
    new EOKey<Date>("dayofdeath");
}
