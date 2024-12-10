/*
  Copyright (C) 2007-2014 Helge Hess

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.eoaccess.EOEntity;
import org.getobjects.eocontrol.EOKey;
import org.getobjects.foundation.NSTimeRange;
import org.getobjects.foundation.UList;
import org.getobjects.foundation.UObject;
import org.getobjects.foundation.UString;
import org.opengroupware.logic.authz.OGoContactPermissionHandler;

/**
 * OGoContact
 * <p>
 * Superclass for all 'company' table objects. That is, persons, teams and
 * enterprises (OGoCompany).
 * <p>
 * Contact objects can have custom attributes in the company_value table,
 * they have an arbitary number of telephone entries and address entries.
 * They can have an ACL attached.
 * <p>
 * Permissions are fetched by the OGoContactPermissionHandler.
 * 
 * <p>
 * @see OGoPerson
 * @see OGoCompany
 * @see OGoContactPermissionHandler
 */
@SuppressWarnings("unchecked")
public abstract class OGoContact extends OGoObject implements IOGoTaggedObject {
  protected static final Log log = LogFactory.getLog("OGoContact");
  
  protected boolean isReadOnlyFlag;
  protected boolean isPrivate;
  
  public Collection<OGoNote>     notes;
  public Collection<OGoDocument> attachments;
  
  public OGoContact(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setIsReadOnlyFlag(final Boolean _flag) {
    this.isReadOnlyFlag = UObject.boolValue(_flag);
  }
  public Boolean isReadOnlyFlag() { // TBD: object result to make it balanced?
    return this.isReadOnlyFlag;
  }
  
  public void setIsPrivate(final Boolean _flag) {
    this.isPrivate = UObject.boolValue(_flag);
  }
  public Boolean isPrivate() { // TBD: object result to make it balanced?
    return this.isPrivate;
  }
  
  
  /* type flags */
  
  public void setIsPerson(final boolean _flag) {
    if (_flag)
      log().error("attempt to set isPerson flag on incorrect contact: " + this);
  }
  public boolean isPerson() {
    return false;
  }
  
  public void setIsTeam(final boolean _flag) {
    if (_flag)
      log().error("attempt to set isTeam flag on incorrect contact: " + this);
  }
  public boolean isTeam() {
    return false;
  }
  
  public void setIsCompany(final boolean _flag) {
    if (_flag)
      log().error("attempt to set isCompany flag on incorrect contact: " +this);
  }
  public boolean isCompany() {
    return false;
  }
  
  public void setIsResource(final boolean _flag) {
    if (_flag)
      log().error("attempt to set isResource flag on incorrect contact: "+this);
  }
  public boolean isResource() {
    return false;
  }
  
  
  /* permissions */
  
  /**
   * Applies contact field restrictions.
   * <ul>
   *   <li><code>l</code> - list (implied by others)  
   *   <li><code>b</code> - business contact data (phone/address)
   *   <li><code>p</code> - private contact data (phone/address)
   *   <li><code>I</code> - IM data
   *   <li><code>P</code> - private data like birthday etc
   *   <li><code>M</code> - mobile number
   *   <li><code>s</code> - may send private messages
   *   <li><code>c</code> - may connect with me in the system?
   * </ul>
   */
  @Override
  public void applyPermissions(String _perms) {
    /* 'r' and 'w' imply access to everything */
    if (_perms.indexOf('r') >= 0) return;
    if (_perms.indexOf('w') >= 0) return;
    
    /* let super continue */
    super.applyPermissions(_perms);
  }
  
  
  /* helpers */
  
  @SuppressWarnings("rawtypes")
  public List<OGoAddress> nonEmptyAddresses() {
    return UList.extractNonEmptyObjects
      ((Collection)this.valueForKey("addresses"));
  }
  
  @SuppressWarnings("rawtypes")
  public List<OGoPhoneNumber> nonEmptyPhones() {
    return UList.extractNonEmptyObjects((Collection)this.valueForKey("phones"));
  }
  
  @SuppressWarnings("rawtypes")
  public List<OGoEMailAddress> nonEmptyEMails() {
    return UList.extractNonEmptyObjects((Collection)this.valueForKey("emails"));
  }
  
  /**
   * Lookup the phone with the specified key in the 'phones' relationship.
   * <p>
   * While OGo usually auto-created proper phone records in the database,
   * you should not rely on that. Depending on the configuration, fewer or more
   * phones might exist!
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
   * Per default an OGo company has those phone numbers types:
   * <ul>
   *   <li>01_tel
   *   <li>02_tel
   *   <li>10_fax
   * </ul>
   * 
   * @param _key - the key of the phone (eg '01_tel' or '05_tel_private')
   * @return an OGoPhoneNumber with the given key
   */
  public OGoPhoneNumber phoneNumberWithKey(final String _key) {
    if (_key == null)
      return null;
    
    final List<OGoPhoneNumber> phones = (List<OGoPhoneNumber>)
      this.valueForKey("phones");
    if (phones == null)
      return null;
    
    for (OGoPhoneNumber phone: phones) {
      if (_key.equals(phone.key()))
        return phone;
    }
    return null;
  }
  
  /**
   * Lookup the address with the specified key in the 'addresses' relationship.
   * <p>
   * While OGo usually auto-created proper address records in the database,
   * you should not rely on that. Depending on the configuration, fewer or more
   * addresses might exist!
   * 
   * <p>
   * Per default an OGo person has those address types:
   * <ul>
   *   <li>private
   *   <li>mailing
   *   <li>location
   * </ul>
   * Per default an OGo company has those address types:
   * <ul>
   *   <li>ship
   *   <li>bill
   * </ul>
   * 
   * @param _key - the key of the address (eg 'location' or 'mailing')
   * @return an OGoAddress with the given key
   */
  public OGoAddress addressWithKey(final String _key) {
    if (_key == null)
      return null;
    
    final List<OGoAddress> addresses = (List<OGoAddress>)
      this.valueForKey("addresses");
    if (addresses == null)
      return null;
    
    for (OGoAddress address: addresses) {
      if (_key.equals(address.key()))
        return address;
    }
    return null;
  }
  
  /**
   * Lookup the extended value with the specified key in the 'extraValues'
   * relationship.
   * <p>
   * Note: extra values can be multivalue and they can be time constrained!
   * 
   * <p>
   * Per default an OGo person has those extra values:
   * <ul>
   *   <li>job_title
   *   <li>email1 (exposed in the 'emails' relationship)
   *   <li>email2 (exposed in the 'emails' relationship)
   * </ul>
   * Per default an OGo company has no extra values.
   * 
   * @param _key - the key of the value (eg 'job_title')
   * @return an OGoContactExtValue with the given key, or null
   */
  public OGoContactExtValue extraValueWithKey(final String _key) {
    if (_key == null)
      return null;
    
    final List<OGoContactExtValue> values = (List<OGoContactExtValue>)
      this.valueForKey("extraValues");
    if (values == null)
      return null;
    
    for (OGoContactExtValue value: values) {
      if (_key.equals(value.key()))
        return value;
    }
    return null;
  }
  /**
   * Lookup the extended values with the specified key in the 'extraValues'
   * relationship.
   * <p>
   * Note: extra values can be multivalue and they can be time constrained!
   * 
   * <p>
   * Per default an OGo person has those extra values:
   * <ul>
   *   <li>job_title
   *   <li>email1 (exposed in the 'emails' relationship)
   *   <li>email2 (exposed in the 'emails' relationship)
   * </ul>
   * Per default an OGo company has no extra values.
   * 
   * @param _key - the key of the value (eg 'job_title')
   * @return a List of OGoContactExtValue's with the given key, or null
   */
  public List<OGoContactExtValue> extraValuesWithKey(final String _key) {
    if (_key == null)
      return null;
    
    final List<OGoContactExtValue> values = (List<OGoContactExtValue>)
      this.valueForKey("extraValues");
    if (values == null)
      return null;
    
    List<OGoContactExtValue> results = null;
    for (OGoContactExtValue value: values) {
      if (_key.equals(value.key())) {
        if (results == null)
          results = new ArrayList<OGoContactExtValue>(16);
        results.add(value);
      }
    }
    return results;
  }
  /**
   * Lookup the extended values with the specified key in the 'extraValues'
   * relationship.
   * <p>
   * Note: extra values can be multivalue and they can be time constrained!
   * 
   * <p>
   * Per default an OGo person has those extra values:
   * <ul>
   *   <li>job_title
   *   <li>email1 (exposed in the 'emails' relationship)
   *   <li>email2 (exposed in the 'emails' relationship)
   * </ul>
   * Per default an OGo company has no extra values.
   * 
   * @param _key   - the key of the value (eg 'job_title')
   * @param _range - start/end_date must overlap with this range
   * @return a List of OGoContactExtValue's with the given key, or null
   */
  public List<OGoContactExtValue> extraValuesWithKey
    (final String _key, final NSTimeRange _range)
  {
    if (_key == null || _range == null)
      return null;
    
    final List<OGoContactExtValue> values = (List<OGoContactExtValue>)
      this.valueForKey("extraValues");
    if (values == null)
      return null;
    
    List<OGoContactExtValue> results = null;
    for (OGoContactExtValue value: values) {
      if (_key.equals(value.key()) && value.isInTimeRange(_range)) {
        if (results == null)
          results = new ArrayList<OGoContactExtValue>(16);
        results.add(value);
      }
    }
    return results;
  }
  /**
   * Lookup the extended values with the specified key in the 'extraValues'
   * relationship.
   * <p>
   * Note: extra values can be multivalue and they can be time constrained!
   * 
   * <p>
   * Per default an OGo person has those extra values:
   * <ul>
   *   <li>job_title
   *   <li>email1 (exposed in the 'emails' relationship)
   *   <li>email2 (exposed in the 'emails' relationship)
   * </ul>
   * Per default an OGo company has no extra values.
   * 
   * @param _key  - the key of the value (eg 'job_title')
   * @param _date - start/end_date must be valid at this date
   * @return a List of OGoContactExtValue's with the given key, or null
   */
  public List<OGoContactExtValue> extraValuesWithKey
    (final String _key, final Date _date)
  {
    if (_key == null)
      return null;
    
    final List<OGoContactExtValue> values = (List<OGoContactExtValue>)
      this.valueForKey("extraValues");
    if (values == null)
      return null;
    
    List<OGoContactExtValue> results = null;
    for (OGoContactExtValue value: values) {
      if (_key.equals(value.key()) &&
          (_date == null || value.isActiveAtDate(_date)))
      {
        if (results == null)
          results = new ArrayList<OGoContactExtValue>(16);
        results.add(value);
      }
    }
    return results;
  }
  
  /**
   * Convenience method which returns the first phone number (aka 01_tel).
   * 
   * @return the first phone number of the contact
   */
  public OGoPhoneNumber firstPhone() {
    return this.phoneNumberWithKey("01_tel");
  }
  /**
   * Convenience method which returns the first fax number (aka 10_fax).
   * 
   * @return the first fax number of the contact
   */
  public OGoPhoneNumber firstFax() {
    return this.phoneNumberWithKey("10_fax");
  }
  
  
  /* convenience */
  
  /**
   * Returns the 'numeric' part of a 'number' code. Eg:<pre>
   *   A1023
   *   M8482
   *   OGo288372</pre>
   * Return 1023, 8482 and 288372.
   * 
   * @return numeric part of the number code, or null
   */
  public Number numberAsNumber() {
    final Object v = this.valueForKey("number");
    if (v == null) return null;
    if (v instanceof Number) return (Number)v;
    
    String s   = (String)v;
    int    len = s.length();
    int    i;
    
    /* locate first digit */
    for (i = 0; i < len && !Character.isDigit(s.charAt(i)); i++)
      ;
    if (i >= len)
      return null; /* did not find digits in the 'number' string */
    
    return UObject.intValue(s.substring(i));
  }
  
  public void setNumberAsNumber(String _prefix, final Number _number) {
    if (_number == null)
      _prefix = null;
    else
      _prefix = _prefix != null ? (_prefix + _number) : ("" + _number);
    
    this.takeValueForKey(_prefix, "number");
  }
  
  
  /* tagging */
  
  /* Note that we split on ', ', with a trailing whitespace! */
  public static final String OGoTagSplitter = ", ";
  public static final String OGoTagProperty = "keywords";
  protected static final OGoKeywordsTaggedObjectHelper tagHelper =
    new OGoKeywordsTaggedObjectHelper(OGoTagProperty, OGoTagSplitter);
  
  public void setTagNames(String[] _tags) {
    tagHelper.setTagNames(this, _tags);
  }
  public String[] tagNames() {
    return tagHelper.tagNames(this);
  }
  
  public boolean hasTag(final String _tag) {
    return tagHelper.hasTag(this, _tag);
  }
  
  public boolean addTag(final String _tag) {
    // TBD: also do an addTags()? or modifyTags(addTags, deleteTags)
    return tagHelper.addTag(this, _tag);
  }
  
  public boolean removeTag(final String _tag) {
    return tagHelper.removeTag(this, _tag);
  }
  
  public void setTagNamesAsCollection(Collection<String> _tags) {
    this.setTagNames(_tags != null ? _tags.toArray(new String[0]) : null);
  }
  public List<String> tagNamesAsList() {
    String[] tags = this.tagNames();
    if (tags == null) return null;
    return Arrays.asList(tags);
  }

  
  /* browser links */

  /**
   * Returns the 'url' field of the contact, but adds 'http://' if required.
   * The user can store arbitrary values in the 'url' field, hence its not
   * directly suitable for use in a hyperlink.
   * <p>
   * Note: in fact you might want to use a referer obfuscator in addition!
   * (w/o it, the referer sent to remote sites might include the session-id,
   * which could then be used for session hijacking)
   * 
   * @return a browser link, like "mailto:info@skyrix.de"
   */
  public String absoluteHomepageURL() {
    String v = (String)this.valueForKey("url");
    if (v == null) return null;
    v = v.trim();
    
    // TBD: improve
    String p = UString.getURLProtocol(v);
    if (p != null) return v;
    return "http://" + v;
  }
  
  /**
   * Returns the 'email' field of the contact, but adds 'mailto:' in front and
   * properly URL escapes the email address.
   * <p>
   * Note: OGoPerson records use a relationship for emails, NOT the 'email'
   * field of the main table.
   * 
   * @return a browser link, like "mailto:info@skyrix.de"
   */
  public String emailAsBrowserLink() {
    String v = (String)this.valueForKey("email");
    if (v == null) return null;
    
    v = v.trim();
    v = UString.stringByEncodingURLComponent(v, null /* utf-8 */);
    return v.length() > 0 ? ("mailto:" + v) : null;
  }
  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
  }

  
  /* keys */
  
  public static final EOKey<Boolean> keyIsReadOnlyFlag =
    new EOKey<Boolean>("isReadOnlyFlag");
  public static final EOKey<Boolean> keyIsPrivate =
    new EOKey<Boolean>("isPrivate");
}
