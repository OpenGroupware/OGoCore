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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.getobjects.foundation.NSKeyValueCoding;
import org.getobjects.foundation.UString;

/**
 * IOGoTaggedObject
 * <p>
 * Interface for OGo objects which can be 'tagged' (can have keywords assigned).
 * 
 * <p>
 * Implementors:
 * <ul>
 *   <li>OGoContact (maps tags to the 'keywords' field)
 * </ul>
 * 
 * <p>
 * @author helge
 */
public interface IOGoTaggedObject {

  /**
   * Returns the names of the tags assigned to the object, eg:
   *   <pre>{ "Customer", "Press", "Friend" }</pre>
   * 
   * @return an array of tag names
   */
  public String[] tagNames();
  
  
  /**
   * OGoKeywordsTaggedObjectHelper
   * <p>
   * Helper class to implement IOGoTaggedObject on an EO object which stores
   * the tags in a single field.
   * For example OGoContact objects store the tags in the 'keywords' attribute,
   * using ", " as a split. Example:<pre>
   *   Customer, Fortune 100, VIP</pre>
   * You need one object helper per property (if you have multiple tagsets on
   * an object, you need multiple helpers), but only one helper per class
   * (since all methods take the object to act on as a parameter).
   */
  public static class OGoKeywordsTaggedObjectHelper {
    protected static final Log log = LogFactory.getLog("OGoTaggedObject");
    
    protected String key;
    protected String splitter;
    
    public OGoKeywordsTaggedObjectHelper(String _key, String _splitter) {
      this.key      = _key      != null ? _key      : "keywords";
      this.splitter = _splitter != null ? _splitter : ", ";
    }
    
    public void setTagNames(final NSKeyValueCoding _o, String[] _tags) {
      if (_o == null)
        return;
      if (_tags == null || _tags.length == 0) {
        _o.takeValueForKey(null, this.key);
        return;
      }
      
      List<String> tags = new ArrayList<String>(_tags.length);
      for (int i = 0; i < _tags.length; i++) {
        String tag = _tags[i];
        if (tag != null) tag = tag.trim();
        if (tag == null || tag.length() == 0)
          continue;
        
        if (tags.contains(tag))
          continue;
        
        // TBD: escape splitter? (hard to do in SQL)
        tags.add(tag);
      }
      Collections.sort(tags);
      
      String s = UString.componentsJoinedByString(tags, this.splitter);
      _o.takeValueForKey(s, this.key);
    }
    
    public String[] tagNames(final NSKeyValueCoding _o) {
      if (_o == null)
        return null;
      String v = (String)_o.valueForKey(this.key);
      if (v == null) return null;
      
      v = v.trim();
      String[] tags = UString.componentsSeparatedByString(v, this.splitter);
      
      int i = 0, j = 0;
      for (; i < tags.length; i++) {
        String tag = tags[i].trim();
        // TBD: unescape tag? (hard to do in SQL)
        if (tag == null || tag.length() == 0)
          continue;
        
        tags[j] = tag;
        j++;
      }
      if (j == 0)
        return null;
      
      if (j < i) {
        final String[] vtags = tags;
        tags = new String[j];
        System.arraycopy(vtags, 0, tags, 0, j);
      }
      
      return tags;
    }
    
    public boolean hasTag(final NSKeyValueCoding _o, String _tag) {
      if (_tag == null)
        return false;
      
      _tag = _tag.trim();
      if (_tag.length() == 0)
        return false;
      
      String v = (String)_o.valueForKey(this.key);
      if (v == null) return false;
      
      if (!v.contains(_tag))
        return false;
      
      final String[] tagNames = this.tagNames(_o);
      if (tagNames == null)
        return false;
      
      for (String tag: tagNames) {
        if (tag != null && tag.equals(_tag))
          return true;
      }
      return false;
    }
    
    public boolean addTag(final NSKeyValueCoding _o, String _tag) {
      // TBD: also do an addTags()? or modifyTags(addTags, deleteTags)
      if (_tag == null)
        return false;
      
      _tag = _tag.trim();
      if (_tag.length() == 0)
        return false;
      
      if (_tag.contains(this.splitter)) {
        log.warn("attempt to add tag containing splitter: " + _tag);
        return false;
      }
      
      if (this.hasTag(_o, _tag))
        return true; /* already in set */

      final String[] oldTagNames = this.tagNames(_o);
      if (oldTagNames == null || oldTagNames.length == 0) {
        /* simple case, just add tag as-is */
      }
      else {
        String[] newTagNames = new String[oldTagNames.length + 1];
        System.arraycopy(oldTagNames, 0, newTagNames, 0, oldTagNames.length);
        newTagNames[oldTagNames.length] = _tag; /* add new tag */
        Arrays.sort(newTagNames); /* sort */
      }
      _o.takeValueForKey(_tag, this.key);
      return true;
    }
    
    public boolean removeTag(final NSKeyValueCoding _o, String _tag) {
      if (_tag == null)
        return false;
      
      _tag = _tag.trim();
      if (_tag.length() == 0)
        return false;
      
      if (_tag.contains(this.splitter)) {
        log.warn("attempt to remove tag containing splitter: " + _tag);
        return false;
      }
      
      if (!this.hasTag(_o, _tag))
        return true; /* not in set */

      final String[] oldTagNames = this.tagNames(_o);
      if (oldTagNames == null || oldTagNames.length == 0)
        return true; /* nothing in set */
      
      final List<String> tags = Arrays.asList(oldTagNames);
      tags.remove(_tag);
      
      String kw;
      if (tags.size() > 0) {
        Collections.sort(tags);
        kw = UString.componentsJoinedByString(tags, this.splitter);
        if (kw != null && kw.length() == 0) kw = null;
      }
      else
        kw = null;
      
      _o.takeValueForKey(kw, this.key);
      return true;
    }
  }
}
