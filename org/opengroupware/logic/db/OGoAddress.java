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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.getobjects.eoaccess.EOEntity;
import org.getobjects.foundation.NSException;
import org.getobjects.foundation.UString;

/**
 * OGoAddress
 * <p>
 * Represents an address attached to a person or company. Addresses are stored
 * in the 'address' table.
 * <p>
 * OGo Types:
 * <ul>
 *   <li>location (HOME,        person)
 *   <li>mailing  (WORK,POSTAL  person)
 *   <li>private  (HOME,DOM     person)
 *   <li>bill     (WORK         company)
 *   <li>ship     (WORK,PARCEL  company)
 * </ul>
 * <p>
 * <em>Important!</em>
 * The keys must be unique for a record since OGo often accesses them using a
 * dictionary keyed on the key (hence the name ;-)
 * <p>
 * vCard samples:
 * <ul>
 *   <li>V:DOM,HOME,INTL,PARCEL,POSTAL,PREF,WORK
 *   <li>V:POSTAL
 * </ul>
 * <ul>
 *   <li>HOME
 *   <li>WORK
 *   <li>DOM  - domestic
 *   <li>INTL - international
 *   <li>PREF - preferred
 *   <li>POSTAL
 *   <li>PARCEL
 * </ul>
 */
public class OGoAddress extends OGoObject
  implements IOGoVCardLabeledObject, IOGoContactChildObject
{
  protected Number companyId;
  protected String key;     /* location, private, ship, bill, mail */
  protected String keyType; /* this is not persistent, its used internally */
  
  public String name1;
  public String name2;
  public String name3;
  
  public String street;
  public String zip;
  public String city;
  public String state;
  public String country;
  
  // TBD: OGoContact contact, 'person' and 'company'
  // TBD: 'Label' generieren (label format DE)
  
  public OGoAddress(final EOEntity _entity) {
    super(_entity);
  }
  
  
  /* accessors */
  
  public void setCompanyId(final Number _pkey) {
    if (_pkey == null && this.companyId != null)
      this.log().warn("attempt to reset companyId of phone: " + this);
    
    this.companyId = _pkey;
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
  
  
  /* check fill state */

  @Override
  public boolean isEmpty() {
    if (this.appliedPermissions != null &&
        this.appliedPermissions.length() == 0)
      return true; /* no permissions on object */
    
    /* sorted by likeliness */
    
    if (this.name1 != null && this.name1.trim().length() > 0)
      return false;
    if (this.city != null && this.city.trim().length() > 0)
      return false;
    if (this.street != null && this.street.trim().length() > 0)
      return false;

    if (this.name2 != null && this.name2.trim().length() > 0)
      return false;
    if (this.name3 != null && this.name3.trim().length() > 0)
      return false;
    if (this.zip != null && this.zip.trim().length() > 0)
      return false; /* zip w/o city? */ // => TBD: add some smartness? :-)
    if (this.state != null && this.state.trim().length() > 0)
      return false;
    
    return true;
  }
  
  public boolean hasNameStreetAndZipCity() {
    if (this.name1  == null || this.city == null ||
        this.street == null || this.zip  == null)
      return false;
    
    if (this.name1.trim().length()  == 0) return false;
    if (this.city.trim().length()   == 0) return false;
    if (this.street.trim().length() == 0) return false;
    if (this.zip.trim().length()    == 0) return false;
    
    return true;
  }
  public boolean hasStreetAndZipCity() {
    // in case the name is built from the contact itself
    if (this.city == null || this.street == null || this.zip  == null)
      return false;
    
    if (this.city.trim().length()   == 0) return false;
    if (this.street.trim().length() == 0) return false;
    if (this.zip.trim().length()    == 0) return false;
    
    return true;
  }

  /* permissions */
  
  public void applyPermissions(String _perms) {
    if (_perms == null || _perms.length() == 0) {
      // TBD: use a special 'forbidden' value
      this.name1   = null;
      this.name2   = null;
      this.name3   = null;
      this.key     = null;
      
      this.street  = null;
      this.zip     = null;
      this.city    = null;
      this.state   = null;
      this.country = null;
    }
  }


  /* vCard type */
  
  public void setKeyType(final String _keyType) {
    this.keyType = _keyType;
  }
  public void setKeyUsingKeyTypeAndSequence(final String _keyType, int _seq) {
    this.takeValueForKey(keyUsingTypeAndSequence(_keyType, _seq), "key");
  }
  public static String keyUsingTypeAndSequence(String _keyType, int _seq) {
    if (_keyType == null)
      return null;
    if (_seq < 1)
      return _keyType; /* eg 'bill' or 'ship' */
    
    return _keyType + _seq; /* eg 'bill1' or 'ship5' */
  }

  public boolean isPreferred() {
    /* only works on vCard fields */
    return this.key != null ? (this.key.indexOf("PREF") != -1) : false;
  }
  
  /**
   * Removes the sequence of the 'key' of the address. Eg if you have two
   * billing addresses, 'bill' and 'bill1', the method will return 'bill'
   * as the keyType for both.
   * 
   * @return the OGo keytype of the address (eg 'bill') 
   */
  public String keyType() {
    if (this.keyType != null)
      return this.keyType;
    
    // bill5 => bill
    String k = this.key;
    int len;
    if (k == null || (len = k.length()) == 0)
      return null;
    
    /* cut off trailing digits (one leading digit is preserved) */
    while (len > 1 && Character.isDigit(k.charAt(len - 1))) {
      k = k.substring(0, len - 1);
      len--;
    }
    return k;
  }
  
  /**
   * Returns 'WORK', 'HOME' or 'OTHER' depending on the type of the address.
   * <p>
   * OGo can store arbitary vCard types with a V: prefix, eg:
   * <pre>V:OTHER,PREF,WORK</pre>
   * or it uses one of its predefined keys:
   * <ul>
   *   <li>location (HOME)         person
   *   <li>mailing  (POSTAL,WORK)  person
   *   <li>private  (DOM,HOME)     person
   *   <li>bill     (WORK)         company
   *   <li>ship     (PARCEL,WORK)  company
   * </ul>
   */
  public String primaryVCardLabel() {
    // TBD: improve
    if (this.key == null)
      return "WORK";

    if (this.key.startsWith("V:")) {
      String labels = this.key.toUpperCase();
      if (labels.contains("WORK"))
        return "WORK";
      if (labels.contains("HOME"))
        return "HOME";
      
      return "OTHER";
    }
    
    /* standard OGo type (bill, location, mailing, private, ship) */
    
    if (this.key.contains("private"))
      return "HOME";
    
    return "WORK";
  }
  
  public String[] vCardLabels() {
    return vCardLabelsForKeyType(this.keyType());
  }
  public String vCardLabelsAsString() {
    return UString.componentsJoinedByString(this.vCardLabels(), ",");
  }

  public void setVCardLabels(String[] _labels) {
    this.setKeyType(OGoAddress.keyTypeForVCardLabels(_labels));
  }
  public void setVCardLabelsAsString(String _labels) {
    this.setVCardLabels(_labels != null ? _labels.split(",") : null);
  }

  /**
   * Map labels to an internal OGo key type. The type is just the prefix of the
   * key, it might needs to be suffixed with the address sequence
   * (eg bill=>bill4).
   * <p>
   * The method converts the input array to uppercase and removes duplicates.
   * The PREF tag is ignored for type processing (just matters for the
   * sequence).
   * <p>
   * Mappings ("PREF" is ignored)
   * <ul>
   *   <li>PREF        => location (just PREF, nothing else)
   *   <li>HOME        => location
   *   <li>WORK        => bill
   *   <li>POSTAL      => ship
   *   <li>DOM         => private
   *   <li>WORK,POSTAL => mailing
   *   <li>WORK,PARCEL => ship
   *   <li>HOME,DOM    => private
   * </ul>
   * 
   * @param _labels - an array of vCard labels
   * @return
   */
  public static String keyTypeForVCardLabels(String[] _labels) {
    if (_labels == null || _labels.length == 0)
      return null;
    
    /* inefficient, but hey! ;-) */
    Set<String> keys = new HashSet<String>(_labels.length);
    for (int i = (_labels.length - 1); (i >= 0); i--) {
      if (_labels[i] != null)
        keys.add(_labels[i].toUpperCase());
    }
    /* PREF does not matter for our key mapping, it might matter for ordering
     * in the address sequence (PREF being bill, not bill4) 
     */
    keys.remove("PREF");

    String type = null;
    int count = keys.size();
    if (count == 0) /* was just PREF (or null ...) */
      type = "location";
    else if (count == 1) {
      if      (keys.contains("WORK"))   type = "bill";
      else if (keys.contains("HOME"))   type = "location";
      else if (keys.contains("POSTAL")) type = "ship";
      else if (keys.contains("DOM"))    type = "private";
    }
    else if (count == 2 && keys.contains("WORK")) {
      if (keys.contains("POSTAL")) type = "mailing"; /* person */
      if (keys.contains("PARCEL")) type = "ship";    /* company */
    }
    else if (count == 2 && keys.contains("HOME")) {
      if (keys.contains("DOM")) type = "private";
    }
    
    
    if (type == null) /* fallback to custom sequence, eg V:WORK,INTL */
      type = "V:" + UString.componentsJoinedByString(keys.toArray(), ",");
    
    return type;
  }
  
  public static String[] vCardLabelsForKeyType(String _type) {
    if (_type == null)
      return null;
    
    if (_type.startsWith("V:")) {
      /* eg: V:WORK,PREF */
      // TBD: should we remove PREF?
      String[] ls = _type.substring(2).split(",");
      Arrays.sort(ls);
      return ls;
    }
    
    if (_type.equals("location")) return labels_location;
    if (_type.equals("bill"))     return labels_bill;
    if (_type.equals("ship"))     return labels_ship;
    if (_type.equals("private"))  return labels_private;
    if (_type.equals("mailing"))  return labels_mailing;

    /* error, unexpected key (can happen if the user mapped additional ones) */
    log.warn("unexpected internal phone type, returned as X-OGo-type: "+_type);
    return new String[] { "X-OGo-" + _type };
  }

  /* Important: keep that sorted, alphabetically. */
  private static final String[] labels_location = { "HOME" };
  private static final String[] labels_private  = { "DOM", "HOME" };
  private static final String[] labels_mailing  = { "POSTAL", "WORK" };
  private static final String[] labels_bill     = { "WORK" };
  private static final String[] labels_ship     = { "PARCEL", "WORK" };


  /**
   * Process key assignments. Walk over each contact and setup the proper
   * key numbering to avoid duplicate keys.
   * This method is called by prepareForTransactionInContext().
   * <p>
   * The implementation for OGoAddresses creates keys like:
   * 'bill', 'bill1', 'bill2' etc.
   * It requires that the contact-IDs are properly ordered.
   * <p>
   * Note: email has different rules. (sequence/key separate from vCard label)
   */
  public static Exception reworkKeySequences
    (List<IOGoContactChildObject> _existingObjects,
     List<IOGoContactChildObject> _newObjects,
     Collection<Number> _deletedIds)
  {
    Map<String, Number> keyTypeToSequence = new HashMap<String, Number>(8);
    
    /* Walk over *all* existing items. Those are properly ordered in the
     * fetch.
     * Check whether an existing item got deleted, in this case its slots
     * can be reused. This implies that other existing items with the same
     * key (eg 'bill1') change their type key (eg if bill1 got deleted, bill2
     * will become bill1 etc).
     */
    if (_existingObjects != null) {
      for (IOGoContactChildObject item: _existingObjects) {
        /* if the item is deleted, it does not consume a slot anymore */
        if (_deletedIds != null && _deletedIds.contains(item.id()))
          continue;

        OGoAddress typed = (OGoAddress)item;

        String keyType = typed.keyType();
        if (keyType == null) {
          log.error("item has no keyType: " + item);
          return new NSException("item has no keytype"); // TBD
        }

        typed.setKeyUsingKeyTypeAndSequence
          (keyType, UOGoObject.nextKey(keyTypeToSequence, keyType));
      }
    }
    
    /* next process new items */

    if (_newObjects != null) {
      for (IOGoContactChildObject item: _newObjects) {
        OGoAddress typed = (OGoAddress)item;

        String keyType = typed.keyType();
        if (keyType == null) {
          log.error("new item has no keyType: " + item);
          return new NSException("item has no keytype"); // TBD
        }

        typed.setKeyUsingKeyTypeAndSequence
          (keyType, UOGoObject.nextKey(keyTypeToSequence, keyType));
      }
    }
    
    return null;
  }


  /* legacy */
  
  @Override
  public String entityNameInOGo5() {
    return "Address";
  }
}
