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
package org.opengroupware.logic.auth;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 * Simple JAAS callback handler which is prefilled with login/pwd and pushes
 * that into JAAS when it is being asked for such.
 * <p>
 * There is also the OGoTokenCallback which works with tokens instead of
 * login/pwd.
 * 
 * @author helge
 */
public class NamePasswordCallbackHandler implements CallbackHandler {
  final protected String name;
  final protected char[] pwd;
  
  public NamePasswordCallbackHandler(final String _name, final String _pwd) {
    this.name = _name;
    this.pwd  = _pwd != null ? _pwd.toCharArray() : new char[0];
  }
  
  public void handle(final Callback[] _callbacks)
    throws IOException, UnsupportedCallbackException
  {
    for (final Callback cb: _callbacks) {
      if (cb instanceof NameCallback)
        ((NameCallback)cb).setName(this.name);
      else if (cb instanceof PasswordCallback)
        ((PasswordCallback)cb).setPassword(this.pwd);
    }
  }
}
