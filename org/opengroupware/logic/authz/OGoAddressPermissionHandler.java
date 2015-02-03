/*
  Copyright (C) 2007 Helge Hess

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
 * OGoAddressPermissionHandler
 * <p>
 * Contact addresses are currently classified by their 'key' property (this
 * is the 'type' column). Which is either an internal OGo constant or a vCard
 * CSV like 'V:POSTAL'.
 * <ul>
 *   <li>if the contact has 'w' access, he has 'rw' access to the address
 *   <li>if the contact has 'r' access, he has 'r' access to the address
 *   <li>then it depends on the type
 * </ul>
 * 
 * <p>
 * OGo Types
 * <ul>
 *   <li>bill     - requires 'b'
 *   <li>location - requires 'b'
 *   <li>mailing  - requires 'b'
 *   <li>private  - requires 'p'
 *   <li>ship     - requires 'b'
 * </ul>
 * 
 * <p>
 * VCard Types
 * <ul>
 *   <li>HOME   - requires 'p'
 *   <li>WORK   - requires 'b'
 *   <li>PREF
 *   <li>DOM
 *   <li>INTL
 *   <li>POSTAL
 *   <li>PARCEL
 *   <li>OTHER
 *   <li>X-
 * </ul>
 * <p>
 * Permissions
 * <ul>
 *   <li><code>r</code> - account can see the address
 *   <li><code>w</code> - account can write the address
 * </ul>
 * 
 * <p>
 * @author helge
 */
public class OGoAddressPermissionHandler
  extends OGoContactOwnedObjectPermissionHandler
{
  public static final IOGoPermissionHandler companyAddress =
    new OGoAddressPermissionHandler();

  public static final IOGoPermissionHandler personAddress =
    new OGoAddressPermissionHandler();
  
  private static final String contactWritePerm    = "rw";
  private static final String contactReadPerm     = "r";
  private static final String contactBusinessPerm = "r";
  private static final String contactPrivatePerm  = "r";
  
  @SuppressWarnings("unchecked")
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
      return contactWritePerm; // 'w' implies 'r'
    if (_contactPerm.indexOf('r') >= 0)
      return contactReadPerm;
    
    if (_object == null && _info == null)
      return null; /* need more info */
    
    String type = _object != null ? (String)_object.valueForKey("key") : null;
    if (type == null && _info != null)
      type = (String)((Map<String, String>)_info).get("type"); // rawrow
    
    if (type != null) {
      if (type.startsWith("V:")) {
        /* vCard based types */
        type = type.toUpperCase();
        
        if ((_contactPerm.indexOf('p') >= 0) && type.contains("HOME")) 
          return contactPrivatePerm;
        if ((_contactPerm.indexOf('b') >= 0) && type.contains("WORK"))
          return contactBusinessPerm;
      }
      else {
        /* OGo type */
        
        if (type.contains("private")) {
          return _contactPerm.indexOf('p') >= 0
            ? contactPrivatePerm : OGoAuthzFetchContext.noPermission;
        }
        else {
          return _contactPerm.indexOf('b') >= 0
            ? contactBusinessPerm : OGoAuthzFetchContext.noPermission;
        }
      }
    }
    else
      log.warn("cannot process OGo address w/o a type: " + _object);
    
    return OGoAuthzFetchContext.noPermission;
  }
}
