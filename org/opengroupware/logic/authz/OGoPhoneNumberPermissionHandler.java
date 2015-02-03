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
package org.opengroupware.logic.authz;

import java.util.Map;

import org.getobjects.foundation.NSKeyValueCoding;

/**
 * OGoPhoneNumberPermissionHandler
 * <p>
 * Contact phone numbers are currently classified by their 'key' property (this
 * is the 'type' column). Which is either an internal OGo constant or a vCard
 * CSV like 'V:WORK,PREF'.
 * 
 * <p>
 * OGo Types
 * <ul>
 *   <li>01_tel         - requires 'b'
 *   <li>02_tel         - requires 'b'
 *   <li>03_tel_funk    - requires 'bM'
 *   <li>05_tel_private - requires 'p'
 *   <li>10_fax         - requires 'b'
 *   <li>15_fax_private - requires 'p'
 *   <li>30_pager       - requires 'bM'
 *   <li>31_other1      - requires 'b'
 *   <li>32_other2      - requires 'b'
 * </ul>
 * 
 * <p>
 * VCard Types
 * <ul>
 *   <li>HOME   - requires 'p'
 *   <li>WORK   - requires 'b'
 *   <li>PREF
 *   <li>VOICE
 *   <li>FAX
 *   <li>MSG
 *   <li>CELL   - requires 'M'
 *   <li>PAGER  - requires 'M'
 *   <li>BBS
 *   <li>MODEM
 *   <li>CAR    - requires 'M'
 *   <li>ISDN
 *   <li>VIDEO
 *   <li>PCS
 *   <li>OTHER
 *   <li>X-
 * </ul>
 * 
 * <p>
 * Permissions
 * <ul>
 *   <li><code>r</code> - number can be shown
 *   <li><code>w</code> - number can be changed, implies 'r'
 * </ul>
 * 
 * <p>
 * Relevant contact permissions
 * <ul>
 *   <li><code>r</code> - number is 'r'
 *   <li><code>w</code> - number is 'rw'
 *   <li><code>b</code> - WORK labels visible (business contact data)
 *   <li><code>p</code> - HOME labels visible (private contact data)
 *   <li><code>M</code> - mobile number
 * </ul>
 * 
 * <p>
 * @author helge
 */
public class OGoPhoneNumberPermissionHandler
  extends OGoContactOwnedObjectPermissionHandler
{
  public static final IOGoPermissionHandler companyPhone =
    new OGoPhoneNumberPermissionHandler();
  public static final IOGoPermissionHandler personPhone =
    new OGoPhoneNumberPermissionHandler();

  @SuppressWarnings("rawtypes")
  @Override
  public String objectPermissionForContactPermission
    (String _contactPerm, NSKeyValueCoding _object, Object _info)
  {
    if (_contactPerm == null)
      return null;
    
    int count = _contactPerm.length();
    if (count == 0) /* no permissions on contact, no permission on object */
      return OGoAuthzFetchContext.noPermission;

    /* scan */
    
    if (_contactPerm.indexOf('w') >= 0)
      return "rw"; // 'w' implies 'r'
    if (_contactPerm.indexOf('r') >= 0)
      return "r";
    
    if (_object == null && _info == null)
      return null; /* need more info */
    
    String type = _object != null ? (String)_object.valueForKey("key"):null;
    if (type == null && _info != null)
      type = (String)((Map)_info).get("type"); // rawrow
    
    if (type != null) {
      if (type.startsWith("V:")) {
        /* vCard based types */
        type = type.toUpperCase();
        
        boolean hasWORK = type.contains("WORK");
        
        if (type.contains("CELL") || type.contains("CAR") ||
            type.contains("PAGER"))
        {
          if (_contactPerm.indexOf('M') == -1) /* no cell permission */
            return OGoAuthzFetchContext.noPermission;
          
          if (!hasWORK && !type.contains("HOME")) {
            /* the number is not further tagged with HOME or WORK, allow */
            return "r";
          }
        }
        
        if ((_contactPerm.indexOf('b') >= 0) && hasWORK)
          return "r";
        if ((_contactPerm.indexOf('p') >= 0) && type.contains("HOME")) 
          return "r";
      }
      else {
        /* OGo type */
        
        boolean hasFunk    = type.contains("_funk") || type.contains("_pager");
        boolean hasPrivate = type.contains("_private");
        
        if (hasFunk && _contactPerm.indexOf('M') == -1) /* no cell permission */
          return OGoAuthzFetchContext.noPermission;

        if (hasPrivate) {
          return _contactPerm.indexOf('p') >= 0
            ? "r" : OGoAuthzFetchContext.noPermission;
        }
        else {
          return _contactPerm.indexOf('b') >= 0
            ? "r" : OGoAuthzFetchContext.noPermission;
        }
      }
    }
    else
      log.warn("cannot process OGo phone number w/o a type: " + _object);
    
    return OGoAuthzFetchContext.noPermission;
  }
}
