/* Decision whether to use 'inline' or not.
   Copyright (C) 2006-2023 Free Software Foundation, Inc.

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

#ifndef _UNISTRING_INLINE_H
#define _UNISTRING_INLINE_H

/* This is like the gl_INLINE macro in gnulib/m4/inline.m4, but makes its
   decision based on defined preprocessor symbols rather than through
   autoconf tests.
   See <https://lists.gnu.org/archive/html/bug-gnulib/2006-11/msg00055.html>  */

/* Test for the 'inline' keyword or equivalent.  ISO C 99 semantics is not
   required, only that 'static inline' works.
   Define 'inline' to a supported equivalent, or to nothing if not supported,
   like AC_C_INLINE does.  Also, define UNISTRING_HAVE_INLINE if 'inline' or an
   equivalent is effectively supported, i.e. if the compiler is likely to
   drop unused 'static inline' functions.  */

#if defined __GNUC__ || defined __clang__
/* GNU C/C++ or clang C/C++.  */
# if defined __NO_INLINE__
/* GCC and clang define __NO_INLINE__ if not optimizing or if -fno-inline is
   specified.  */
#  define UNISTRING_HAVE_INLINE 0
# else
/* Whether 'inline' has the old GCC semantics or the ISO C 99 semantics,
   does not matter.  */
#  define UNISTRING_HAVE_INLINE 1
#  ifndef inline
#   define inline __inline__
#  endif
# endif
#elif defined __cplusplus
/* Any other C++ compiler.  */
# define UNISTRING_HAVE_INLINE 1
#else
/* Any other C compiler.  */
# if defined __osf__
/* OSF/1 cc has inline.  */
#  define UNISTRING_HAVE_INLINE 1
# elif defined _AIX || defined __sgi
/* AIX 4 xlc, IRIX 6.5 cc have __inline.  */
#  define UNISTRING_HAVE_INLINE 1
#  ifndef inline
#   define inline __inline
#  endif
# else
/* Some older C compiler.  */
#  define UNISTRING_HAVE_INLINE 0
# endif
#endif

#endif /* _UNISTRING_INLINE_H */
