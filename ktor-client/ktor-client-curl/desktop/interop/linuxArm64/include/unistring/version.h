/* Meta information about GNU libunistring.
   Copyright (C) 2009-2010 Free Software Foundation, Inc.
   Written by Bruno Haible <bruno@clisp.org>, 2009.

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

#ifndef _UNISTRING_VERSION_H
#define _UNISTRING_VERSION_H

/* Get LIBUNISTRING_DLL_VARIABLE.  */
#include <unistring/woe32dll.h>


#ifdef __cplusplus
extern "C" {
#endif


/* Version number: (major<<16) + (minor<<8) + subminor
   except that for versions <= 0.9.3 the value was 0x000009. */
#define _LIBUNISTRING_VERSION 0x010100
extern LIBUNISTRING_DLL_VARIABLE const int _libunistring_version; /* Likewise */


#ifdef __cplusplus
}
#endif


#endif /* _UNISTRING_VERSION_H */
