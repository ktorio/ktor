/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Elementary types and macros for the GNU UniString library.
   Copyright (C) 2002, 2005-2006, 2009-2022 Free Software Foundation, Inc.

   This file is free software: you can redistribute it and/or modify
   it under the terms of the GNU Lesser General Public License as
   published by the Free Software Foundation; either version 2.1 of the
   License, or (at your option) any later version.

   This file is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with this program.  If not, see <https://www.gnu.org/licenses/>.  */

#ifndef _UNITYPES_H
#define _UNITYPES_H

/* Get uint8_t, uint16_t, uint32_t.  */
#include <unistring/stdint.h>

/* Type representing a Unicode character.  */
typedef uint32_t ucs4_t;

/* Attribute of a function whose result depends only on the arguments
   (not pointers!) and which has no side effects.  */
#ifndef _UC_ATTRIBUTE_CONST
# if __GNUC__ > 2 || (__GNUC__ == 2 && __GNUC_MINOR__ >= 95) || defined __clang__
#  define _UC_ATTRIBUTE_CONST __attribute__ ((__const__))
# else
#  define _UC_ATTRIBUTE_CONST
# endif
#endif

/* Attribute of a function whose result depends only on the arguments
   (possibly pointers) and global memory, and which has no side effects.  */
#ifndef _UC_ATTRIBUTE_PURE
# if __GNUC__ > 2 || (__GNUC__ == 2 && __GNUC_MINOR__ >= 96) || defined __clang__
#  define _UC_ATTRIBUTE_PURE __attribute__ ((__pure__))
# else
#  define _UC_ATTRIBUTE_PURE
# endif
#endif

/* Qualifier in a function declaration, that asserts that the caller must
   pass a pointer to a different object in the specified pointer argument
   than in the other pointer arguments.  */
#ifndef _UC_RESTRICT
# if defined __restrict \
     || 2 < __GNUC__ + (95 <= __GNUC_MINOR__) \
     || __clang_major__ >= 3
#  define _UC_RESTRICT __restrict
# elif 199901L <= __STDC_VERSION__ || defined restrict
#  define _UC_RESTRICT restrict
# else
#  define _UC_RESTRICT
# endif
#endif

#endif /* _UNITYPES_H */
