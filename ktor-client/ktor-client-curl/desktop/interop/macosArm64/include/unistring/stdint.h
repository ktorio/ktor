/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
#include <stddef.h>
#if __GLIBC__ >= 2
#include <stdint.h>
#else
/* Copyright (C) 2001-2002, 2004-2010 Free Software Foundation, Inc.
   Written by Paul Eggert, Bruno Haible, Sam Steingold, Peter Burwood.
   This file is part of gnulib.

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU Lesser General Public License as published by
   the Free Software Foundation; either version 2, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with this program; if not, see <https://www.gnu.org/licenses/>.  */

/*
 * Subset of ISO C 99 <stdint.h> for platforms that lack it.
 * <http://www.opengroup.org/susv3xbd/stdint.h.html>
 */

#ifndef _UNISTRING_STDINT_H

/* When including a system file that in turn includes <inttypes.h>,
   use the system <inttypes.h>, not our substitute.  This avoids
   problems with (for example) VMS, whose <sys/bitypes.h> includes
   <inttypes.h>.  */
#define _GL_JUST_INCLUDE_SYSTEM_INTTYPES_H

/* Get those types that are already defined in other system include
   files, so that we can "#define int8_t signed char" below without
   worrying about a later system include file containing a "typedef
   signed char int8_t;" that will get messed up by our macro.  Our
   macros should all be consistent with the system versions, except
   for the "fast" types and macros, which we recommend against using
   in public interfaces due to compiler differences.  */

#if defined __MINGW32__ || defined __HAIKU__ || ((__GNUC__ > 4 || (__GNUC__ == 4 && __GNUC_MINOR__ >= 5)) && !defined __NetBSD__)
# if defined __sgi && ! defined __c99
   /* Bypass IRIX's <stdint.h> if in C89 mode, since it merely annoys users
      with "This header file is to be used only for c99 mode compilations"
      diagnostics.  */
#  define __STDINT_H__
# endif
  /* Other systems may have an incomplete or buggy <stdint.h>.
     Include it before <inttypes.h>, since any "#include <stdint.h>"
     in <inttypes.h> would reinclude us, skipping our contents because
     _UNISTRING_STDINT_H is defined.
     The include_next requires a split double-inclusion guard.  */
# if __GNUC__ >= 3

# endif
# include <stdint.h>
#endif

#if ! defined _UNISTRING_STDINT_H && ! defined _GL_JUST_INCLUDE_SYSTEM_STDINT_H
#define _UNISTRING_STDINT_H

/* <sys/types.h> defines some of the stdint.h types as well, on glibc,
   IRIX 6.5, and OpenBSD 3.8 (via <machine/types.h>).
   AIX 5.2 <sys/types.h> isn't needed and causes troubles.
   MacOS X 10.4.6 <sys/types.h> includes <stdint.h> (which is us), but
   relies on the system <stdint.h> definitions, so include
   <sys/types.h> after <stdint.h>.  */
#if 1 && ! defined _AIX
# include <sys/types.h>
#endif

/* Get LONG_MIN, LONG_MAX, ULONG_MAX.  */
#include <limits.h>

#if defined __MINGW32__ || defined __HAIKU__
  /* In OpenBSD 3.8, <inttypes.h> includes <machine/types.h>, which defines
     int{8,16,32,64}_t, uint{8,16,32,64}_t and __BIT_TYPES_DEFINED__.
     <inttypes.h> also defines intptr_t and uintptr_t.  */
# include <inttypes.h>
#elif 0
  /* Solaris 7 <sys/inttypes.h> has the types except the *_fast*_t types, and
     the macros except for *_FAST*_*, INTPTR_MIN, PTRDIFF_MIN, PTRDIFF_MAX.  */
# include <sys/inttypes.h>
#endif

#if 0 && ! defined __BIT_TYPES_DEFINED__
  /* Linux libc4 >= 4.6.7 and libc5 have a <sys/bitypes.h> that defines
     int{8,16,32,64}_t and __BIT_TYPES_DEFINED__.  In libc5 >= 5.2.2 it is
     included by <sys/types.h>.  */
# include <sys/bitypes.h>
#endif

#undef _GL_JUST_INCLUDE_SYSTEM_INTTYPES_H


/* 7.18.1.1. Exact-width integer types */

/* Here we assume a standard architecture where the hardware integer
   types have 8, 16, 32, optionally 64 bits.  */

#undef int8_t
#undef uint8_t
typedef signed char unistring_int8_t;
typedef unsigned char unistring_uint8_t;
#define int8_t unistring_int8_t
#define uint8_t unistring_uint8_t

#undef int16_t
#undef uint16_t
typedef short int unistring_int16_t;
typedef unsigned short int unistring_uint16_t;
#define int16_t unistring_int16_t
#define uint16_t unistring_uint16_t

#undef int32_t
#undef uint32_t
typedef int unistring_int32_t;
typedef unsigned int unistring_uint32_t;
#define int32_t unistring_int32_t
#define uint32_t unistring_uint32_t

/* Avoid collision with Solaris 2.5.1 <pthread.h> etc.  */
#define _UINT8_T
#define _UINT32_T


#endif /* _UNISTRING_STDINT_H */
#endif /* !defined _UNISTRING_STDINT_H && !defined _GL_JUST_INCLUDE_SYSTEM_STDINT_H */
#endif
