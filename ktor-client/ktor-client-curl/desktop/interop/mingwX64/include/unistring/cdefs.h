/* Common macro definitions for C include files.
   Copyright (C) 2008-2021 Free Software Foundation, Inc.

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

#ifndef _UNISTRING_CDEFS_H
#define _UNISTRING_CDEFS_H

/* _GL_UNUSED_PARAMETER is a marker that can be prepended to function parameter
   declarations for parameters that are not used.  This helps to reduce
   warnings, such as from GCC -Wunused-parameter.  The syntax is as follows:
       _GL_UNUSED_PARAMETER type param
   or more generally
       _GL_UNUSED_PARAMETER param_decl
   For example:
       _GL_UNUSED_PARAMETER int param
       _GL_UNUSED_PARAMETER int *(*param) (void)
 */
#ifndef _GL_UNUSED_PARAMETER
# define _GL_UNUSED_PARAMETER _UC_ATTRIBUTE_MAYBE_UNUSED
#endif
/* _GL_ATTRIBUTE_MAYBE_UNUSED likewise.  */
#ifndef _GL_ATTRIBUTE_MAYBE_UNUSED
# define _GL_ATTRIBUTE_MAYBE_UNUSED _UC_ATTRIBUTE_MAYBE_UNUSED
#endif

#ifndef _GL_ATTRIBUTE_MALLOC
# define _GL_ATTRIBUTE_MALLOC _UC_ATTRIBUTE_MALLOC
#endif

/* _GL_ATTRIBUTE_DEALLOC (F, I) is for functions returning pointers
   that can be freed by passing them as the Ith argument to the
   function F.  _UC_ATTRIBUTE_DEALLOC_FREE is for functions that
   return pointers that can be freed via 'free'; it can be used
   only after including stdlib.h.  These macros cannot be used on
   inline functions.  */
#ifndef _GL_ATTRIBUTE_DEALLOC
# define _GL_ATTRIBUTE_DEALLOC _UC_ATTRIBUTE_DEALLOC
#endif
#ifndef _GL_ATTRIBUTE_DEALLOC_FREE
# define _GL_ATTRIBUTE_DEALLOC_FREE _UC_ATTRIBUTE_DEALLOC_FREE
#endif

/* The definitions below are taken from gnulib/m4/gnulib-common.m4,
   with prefix _UC instead of prefix _GL.  */

/* True if the compiler says it groks GNU C version MAJOR.MINOR.  */
#if defined __GNUC__ && defined __GNUC_MINOR__
# define _UC_GNUC_PREREQ(major, minor) \
    ((major) < __GNUC__ + ((minor) <= __GNUC_MINOR__))
#else
# define _UC_GNUC_PREREQ(major, minor) 0
#endif

#if (defined __has_attribute \
     && (!defined __clang_minor__ \
         || 3 < __clang_major__ + (5 <= __clang_minor__)))
# define _UC_HAS_ATTRIBUTE(attr) __has_attribute (__##attr##__)
#else
# define _UC_HAS_ATTRIBUTE(attr) _UC_ATTR_##attr
# define _UC_ATTR_malloc _UC_GNUC_PREREQ (3, 0)
# define _UC_ATTR_unused _UC_GNUC_PREREQ (2, 7)
#endif

#ifdef __has_c_attribute
# define _UC_HAS_C_ATTRIBUTE(attr) __has_c_attribute (__##attr##__)
#else
# define _UC_HAS_C_ATTRIBUTE(attr) 0
#endif

#if _UC_GNUC_PREREQ (11, 0)
# define _UC_ATTRIBUTE_DEALLOC(f, i) __attribute__ ((__malloc__ (f, i)))
#else
# define _UC_ATTRIBUTE_DEALLOC(f, i)
#endif
#define _UC_ATTRIBUTE_DEALLOC_FREE _UC_ATTRIBUTE_DEALLOC (free, 1)

#if _UC_HAS_ATTRIBUTE (malloc)
# define _UC_ATTRIBUTE_MALLOC __attribute__ ((__malloc__))
#else
# define _UC_ATTRIBUTE_MALLOC
#endif

#if _UC_HAS_C_ATTRIBUTE (maybe_unused)
# define _UC_ATTRIBUTE_MAYBE_UNUSED [[__maybe_unused__]]
#else
# define _UC_ATTRIBUTE_MAYBE_UNUSED _UC_ATTRIBUTE_UNUSED
#endif

#if _UC_HAS_ATTRIBUTE (unused)
# define _UC_ATTRIBUTE_UNUSED __attribute__ ((__unused__))
#else
# define _UC_ATTRIBUTE_UNUSED
#endif

#endif /* _UNISTRING_CDEFS_H */
