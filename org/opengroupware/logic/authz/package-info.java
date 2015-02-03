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
/**
 * <h3>authz</h3>
 * This package contains classes to manage authorization. This means the classes
 * decide whether a user, represented by an OGoLoginContext, has access to some
 * OGo object.
 * <p>
 * The implementation tries to be very efficient with fetches ... which makes it
 * a bit hard to understand ;-)<br>
 * It does all which is done by Objective-C OGo plus some additional features.
 * <p>
 * Note: the package is only used to determine whether a user has a certain
 * permission on an object. It does NOT enforce the permission, this is done by
 * the database objects themselves (applyPermissions() method of OGoObject).
 * <p>
 * FIXME: explain everything ;-)
 * <ul>
 *   <li>ACLs
 *   <li>traversal of object hierarchies
 *   <li>project attached objects
 *   <li>field level permissions
 *   <li>object permission flags ('l', 'r', 'w')
 * </ul>
 *
 * <h4>Implementation</h4>
 * The main entry point is OGoAuthzFetchContext. Its created when the
 * OGoObjectContext fetches permissions for a set of global-ids.
 * 
 * <p>
 * @author helge
 */
package org.opengroupware.logic.authz;
