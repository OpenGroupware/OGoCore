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
 * Note: we are slowly moving this to use JAAS
 * 
 * <h3>auth</h3>
 * 
 * This package contains classes which manage 'authentication' of an OGo user.
 * Do not mix that up with 'authorization', which is about checking object
 * permissions for an authenticated user.
 * 'Authentication' just checks whether the user (login) is who he claims,
 * usually by checking a password. (though we also support token-auth!)
 *
 *
 * <h4>OGoLoginContext</h4>
 * This class manages an authenticated user. An object of this class is returned
 * by an IOGoAuthenticator after successful identification (aka login).
 * <p>
 * A login context is used by the authentication (authz) machinery to check
 * whether an object might be accessed by the authenticated user.
 * <p>
 * Note: a login context might represent multiple accounts, eg if a user is the
 * delegate of another user. He will have the permissions of himself plus the
 * permissions of the user he is a delegate for.
 *
 * <h4>IOGoAuthenticator</h4>
 * 
 * Interface for objects able to provide an OGoLoginContext. It provides a login
 * and a logout method.
 * <p>
 * Note that a logout might not be mandatory, so you should not just expire
 * sessions but properly logout on session termination.
 *
 * <h4>OGoDatabaseAuthenticator (implements IOGoAuthenticator)</h4>
 * The default authenticator which checks login credentials against the OGo user
 * database.
 *
 * <h4>OGoLoginTokenManager</h4>
 * Manages OGo login tokens. Conceptually a token wraps the login information,
 * that is, it would store the login/password and resurrect an OGoLoginContext
 * when required.
 * <p>
 * [TBD: is this complete?]
 * 
 * <p>
 * @author helge
 */
package org.opengroupware.logic.auth;
