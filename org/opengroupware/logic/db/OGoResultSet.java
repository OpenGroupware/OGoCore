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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.getobjects.foundation.NSObject;

/**
 * A helper object to represent the results of a fetch. Thats not just the
 * List of objects, but also whether a limit was hit and whether permissions
 * checks got run.
 * <p>
 * It partially implements the List/Collection interface, hence you can
 * directly use it in most situations.
 * <p>
 * After a fetch you might want to check for:
 * <ul>
 *   <li>hasError() / error()
 *   <li>didHitLimit()
 *   <li>didCheckPermissions()
 * </ul>
 * If you want to retrieve the actual List, just call objects().
 * 
 * @author helge
 */
@SuppressWarnings("rawtypes")
public class OGoResultSet extends NSObject implements List {
  // FIXME: do we want to make this a generic?

  public Exception error;
  public List      objects;
  public boolean   hitLimit;
  public int       limit;
  public boolean   didCheckPermissions;
  
  public OGoResultSet(final Exception _error) {
    super();
    this.error = _error;
    
    if (this.error instanceof NullPointerException)
      this.error.printStackTrace();
  }
  
  public OGoResultSet
    (List _objects, int _limit, boolean _hitLimit, boolean _didCheckPerms)
  {
    super();
    this.objects  = _objects;
    this.limit    = _limit;
    this.hitLimit = _hitLimit;
    this.didCheckPermissions = _didCheckPerms;
  }

  /* accessors */
  
  public boolean hasError() {
    return this.error != null;
  }
  public Exception error() {
    return this.error;
  }
  
  public List objects() {
    return this.objects;
  }
  
  public boolean didHitLimit() {
    return this.hitLimit;
  }
  
  public int limit() {
    return this.limit;
  }

  public boolean didCheckPermissions() {
    return this.didCheckPermissions;
  }
  
  /* empty */
  
  @Override
  public boolean isEmpty() {
    /* Note: we do not treat errors as content. */
    return this.objects == null || this.objects.size() == 0;
  }

  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.error != null)
      _d.append(" error=" + this.error);
    
    if (this.hitLimit)
      _d.append(" hit-limit: " + this.limit);
    
    if (this.objects != null) {
      int count = this.objects.size();
      if (count == 0)
        _d.append(" empty");
      else if (count == 1)
        _d.append(" object=" + this.objects.get(0));
      else if (count < 5)
        _d.append(" objects=" + this.objects);
      else
        _d.append(" #objects=" + count);
    }
    else if (this.error == null)
      _d.append(" no-results?");
  }
  
  
  /* List emulation */

  public boolean contains(Object o) {
    return this.objects != null ? this.objects.contains(o) : false;
  }
  @SuppressWarnings("unchecked")
  public boolean containsAll(Collection c) {
    return this.objects != null ? this.objects.containsAll(c) : false;
  }
  public Object get(int _idx) {
    return this.objects != null ? this.objects.get(_idx) : false;
  }

  public int indexOf(Object o) {
    return this.objects != null ? this.objects.indexOf(o) : -1;
  }
  public int lastIndexOf(Object o) {
    return this.objects != null ? this.objects.lastIndexOf(o) : -1;
  }

  public Iterator iterator() {
    return this.objects != null ? this.objects.iterator() : null;
  }
  public ListIterator listIterator() {
    return this.objects != null ? this.objects.listIterator() : null;
  }
  public ListIterator listIterator(int index) {
    return this.objects != null ? this.objects.listIterator(index) : null;
  }

  public boolean add(Object o) {
    return false;
  }
  public void add(int index, Object element) {
    throw new UnsupportedOperationException();
  }
  public boolean addAll(Collection c) {
    return false;
  }
  public boolean addAll(int index, Collection c) {
    return false;
  }

  public void clear() {
    throw new UnsupportedOperationException();
  }

  public boolean remove(final Object o) {
    return false;
  }
  public Object remove(final int index) {
    return null;
  }
  public boolean removeAll(final Collection c) {
    return false;
  }
  public boolean retainAll(final Collection c) {
    return false;
  }

  public Object set(final int index, final Object element) {
    throw new UnsupportedOperationException();
  }

  public int size() {
    return this.objects != null ? this.objects.size() : 0;
  }

  public List subList(final int fromIndex, final int toIndex) {
    return this.objects != null ? this.objects.subList(fromIndex, toIndex):null;
  }

  public Object[] toArray() {
    return this.objects != null ? this.objects.toArray() : null;
  }

  @SuppressWarnings("unchecked")
  public Object[] toArray(final Object[] a) {
    return this.objects != null ? this.objects.toArray(a) : null;
  }

}
