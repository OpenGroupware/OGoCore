/*
  Copyright (C) 2008-2014 Helge Hess

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
package org.opengroupware.logic.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.security.auth.callback.Callback;

import org.getobjects.foundation.NSObject;

/**
 * This JAAS Callback object is used to request the available tokens from the
 * CallbackHandler.
 * 
 * @author helge
 */
public class OGoTokenCallback extends NSObject implements Callback {

  protected ArrayList<String> tokens;
  protected Object environment;
  
  public OGoTokenCallback() {
  }
  
  
  /* accessors */
  
  public List<String> getTokens() {
    return this.tokens;
  }
  
  public void setTokens(final Collection<String> _tokens) {
    this.tokens = _tokens != null ? new ArrayList<String>(_tokens) : null;
  }
  public void addToken(final String _token) {
    if (_token == null || _token.length() == 0)
      return;
    
    if (this.tokens == null)
      this.tokens = new ArrayList<String>(4);
    this.tokens.add(_token);
  }
  
  public void setEnvironment(Object _env) {
    this.environment = _env;
  }
  public Object getEnvironment() {
    return this.environment;
  }

  
  /* description */

  @Override
  public void appendAttributesToDescription(final StringBuilder _d) {
    super.appendAttributesToDescription(_d);
    
    if (this.tokens == null || this.tokens.size() == 0)
      _d.append(" no-tokens");
    else {
      _d.append(" #tokens=");
      _d.append(this.tokens.size());
    }

    if (this.environment == null)
      _d.append(" no-env");
    else {
      _d.append(" env=");
      _d.append(this.environment);
    }
  }
}
