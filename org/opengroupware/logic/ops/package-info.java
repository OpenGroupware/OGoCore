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
 * <h3>OGo logic.ops</h3>
 *
 * This is similiar to the Logic commands in Objective-C OGo.
 * <p>
 * In OGo we do not really use plain ORM object mapping (eg EOEditingContext) to
 * save changes, but rather implement the things to be done as 
 * 'core.IOGoOperation' objects.
 * <p>
 * FIXME: Find out how we could generalize this concept in GETobjects. Maybe the
 * ops are just advanced EODatabaseOperation objects and the
 * IOGoObjectTransaction should be done by EODatabaseChannel?
 *
 * <h4>Documentation</h4>
 * Read the JavaDocs of
 * <ul>
 *   <li>IOGoOperation
 *   <li>IOGoObjectTransaction
 *   <li>IOGoObjectContext.performOperations()
 * </ul>
 * <p>
 * @author helge
 */
package org.opengroupware.logic.ops;
