/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Association between Unicode characters and their names.
   Copyright (C) 2000-2002, 2005, 2007, 2009-2022 Free Software Foundation,
   Inc.

   This file is free software.
   It is dual-licensed under "the GNU LGPLv3+ or the GNU GPLv2+".
   You can redistribute it and/or modify it under either
     - the terms of the GNU Lesser General Public License as published
       by the Free Software Foundation, either version 3, or (at your
       option) any later version, or
     - the terms of the GNU General Public License as published by the
       Free Software Foundation; either version 2, or (at your option)
       any later version, or
     - the same dual license "the GNU LGPLv3+ or the GNU GPLv2+".

   This file is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License and the GNU General Public License
   for more details.

   You should have received a copy of the GNU Lesser General Public
   License and of the GNU General Public License along with this
   program.  If not, see <https://www.gnu.org/licenses/>.  */

#ifndef _UNINAME_H
#define _UNINAME_H

#include "unitypes.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Required size of buffer for a Unicode character name.  */
#define UNINAME_MAX 256

/* Looks up the name of a Unicode character, in uppercase ASCII.
   Returns the filled buf, or NULL if the character does not have a name.  */
extern char *
       unicode_character_name (ucs4_t uc, char *buf);

/* Looks up the Unicode character with a given name, in upper- or lowercase
   ASCII.  Returns the character if found, or UNINAME_INVALID if not found.  */
extern ucs4_t
       unicode_name_character (const char *name)
       _UC_ATTRIBUTE_PURE;
#define UNINAME_INVALID ((ucs4_t) 0xFFFF)

#ifdef __cplusplus
}
#endif

#endif /* _UNINAME_H */
