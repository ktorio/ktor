/* Support for variables in shared libraries on Windows platforms.
   Copyright (C) 2009 Free Software Foundation, Inc.

   This program is free software: you can redistribute it and/or
   modify it under the terms of either:

     * the GNU Lesser General Public License as published by the Free
       Software Foundation; either version 3 of the License, or (at your
       option) any later version.

   or

     * the GNU General Public License as published by the Free
       Software Foundation; either version 2 of the License, or (at your
       option) any later version.

   or both in parallel, as here.
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with this program.  If not, see <https://www.gnu.org/licenses/>.  */

/* Written by Bruno Haible <bruno@clisp.org>, 2009.  */

#ifndef _UNISTRING_WOE32DLL_H
#define _UNISTRING_WOE32DLL_H

#ifdef IN_LIBUNISTRING
/* All code is collected in a single library,  */
# define LIBUNISTRING_DLL_VARIABLE
#else
/* References from outside of libunistring.  */
# define LIBUNISTRING_DLL_VARIABLE 
#endif

#endif /* _UNISTRING_WOE32DLL_H */
