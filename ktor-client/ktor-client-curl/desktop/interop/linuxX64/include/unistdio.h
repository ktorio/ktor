/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Elementary Unicode string functions.
   Copyright (C) 2002, 2005-2007, 2009-2024 Free Software Foundation, Inc.

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

#ifndef _UNISTDIO_H
#define _UNISTDIO_H

#include "unitypes.h"

/* Get size_t.  */
#include <stddef.h>

/* Get FILE.  */
#include <stdio.h>

/* Get va_list.  */
#include <stdarg.h>

#ifdef __cplusplus
extern "C" {
#endif

/* These work like the printf function family.
   In the format string:
   The format directive 'U' takes an UTF-8 string (const uint8_t *).
   The format directive 'lU' takes an UTF-16 string (const uint16_t *).
   The format directive 'llU' takes an UTF-32 string (const uint32_t *).

   The prefix (ulc_, u8_, u16_, u16_) indicates the type of the resulting
   string.  The prefix 'ulc' stands for "locale encoded".

   An infix 'v' indicates that a va_list is passed instead of multiple
   arguments.

   The functions *sprintf have a 'buf' argument that is assumed to be large
   enough.  (DANGEROUS!  Overflowing the buffer will crash the program.)
   The functions *snprintf have a 'buf' argument that is assumed to be 'size'
   units large.  (DANGEROUS!  The resulting string might be truncated in the
   middle of a multibyte character.)
   The functions *asprintf have a 'resultp' argument.  The result will be
   freshly allocated and stored in *resultp.
   The functions *asnprintf have a (resultbuf, lengthp) argument pair.  If
   resultbuf is not NULL and the result fits into *lengthp units, it is put
   in resultbuf, and resultbuf is returned.  Otherwise, a freshly allocated
   string is returned.  In both cases, *lengthp is set to the length (number
   of units) of the returned string.  In case of error, NULL is returned and
   errno is set.
 */

/* ASCII format string, result in locale dependent encoded 'char *'.  */
extern int
       ulc_sprintf (char *_UC_RESTRICT buf,
                    const char *format, ...);
extern int
       ulc_snprintf (char *_UC_RESTRICT buf, size_t size,
                     const char *format, ...);
extern int
       ulc_asprintf (char **resultp,
                     const char *format, ...);
extern char *
       ulc_asnprintf (char *_UC_RESTRICT resultbuf, size_t *lengthp,
                      const char *format, ...);
extern int
       ulc_vsprintf (char *_UC_RESTRICT buf,
                     const char *format, va_list ap);
extern int
       ulc_vsnprintf (char *_UC_RESTRICT buf, size_t size,
                      const char *format, va_list ap);
extern int
       ulc_vasprintf (char **resultp,
                      const char *format, va_list ap);
extern char *
       ulc_vasnprintf (char *_UC_RESTRICT resultbuf, size_t *lengthp,
                       const char *format, va_list ap);

/* ASCII format string, result in UTF-8 format.  */
extern int
       u8_sprintf (uint8_t *buf,
                   const char *format, ...);
extern int
       u8_snprintf (uint8_t *buf, size_t size,
                    const char *format, ...);
extern int
       u8_asprintf (uint8_t **resultp,
                    const char *format, ...);
extern uint8_t *
       u8_asnprintf (uint8_t *resultbuf, size_t *lengthp,
                     const char *format, ...);
extern int
       u8_vsprintf (uint8_t *buf,
                    const char *format, va_list ap);
extern int
       u8_vsnprintf (uint8_t *buf, size_t size,
                     const char *format, va_list ap);
extern int
       u8_vasprintf (uint8_t **resultp,
                     const char *format, va_list ap);
extern uint8_t *
       u8_vasnprintf (uint8_t *resultbuf, size_t *lengthp,
                      const char *format, va_list ap);

/* UTF-8 format string, result in UTF-8 format.  */
extern int
       u8_u8_sprintf (uint8_t *_UC_RESTRICT buf,
                      const uint8_t *format, ...);
extern int
       u8_u8_snprintf (uint8_t *_UC_RESTRICT buf, size_t size,
                       const uint8_t *format, ...);
extern int
       u8_u8_asprintf (uint8_t **resultp,
                       const uint8_t *format, ...);
extern uint8_t *
       u8_u8_asnprintf (uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp,
                        const uint8_t *format, ...);
extern int
       u8_u8_vsprintf (uint8_t *_UC_RESTRICT buf,
                       const uint8_t *format, va_list ap);
extern int
       u8_u8_vsnprintf (uint8_t *_UC_RESTRICT buf, size_t size,
                        const uint8_t *format, va_list ap);
extern int
       u8_u8_vasprintf (uint8_t **resultp,
                        const uint8_t *format, va_list ap);
extern uint8_t *
       u8_u8_vasnprintf (uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp,
                         const uint8_t *format, va_list ap);

/* ASCII format string, result in UTF-16 format.  */
extern int
       u16_sprintf (uint16_t *buf,
                    const char *format, ...);
extern int
       u16_snprintf (uint16_t *buf, size_t size,
                     const char *format, ...);
extern int
       u16_asprintf (uint16_t **resultp,
                     const char *format, ...);
extern uint16_t *
       u16_asnprintf (uint16_t *resultbuf, size_t *lengthp,
                      const char *format, ...);
extern int
       u16_vsprintf (uint16_t *buf,
                     const char *format, va_list ap);
extern int
       u16_vsnprintf (uint16_t *buf, size_t size,
                      const char *format, va_list ap);
extern int
       u16_vasprintf (uint16_t **resultp,
                      const char *format, va_list ap);
extern uint16_t *
       u16_vasnprintf (uint16_t *resultbuf, size_t *lengthp,
                       const char *format, va_list ap);

/* UTF-16 format string, result in UTF-16 format.  */
extern int
       u16_u16_sprintf (uint16_t *_UC_RESTRICT buf,
                        const uint16_t *format, ...);
extern int
       u16_u16_snprintf (uint16_t *_UC_RESTRICT buf, size_t size,
                         const uint16_t *format, ...);
extern int
       u16_u16_asprintf (uint16_t **resultp,
                         const uint16_t *format, ...);
extern uint16_t *
       u16_u16_asnprintf (uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp,
                          const uint16_t *format, ...);
extern int
       u16_u16_vsprintf (uint16_t *_UC_RESTRICT buf,
                         const uint16_t *format, va_list ap);
extern int
       u16_u16_vsnprintf (uint16_t *_UC_RESTRICT buf, size_t size,
                          const uint16_t *format, va_list ap);
extern int
       u16_u16_vasprintf (uint16_t **resultp,
                          const uint16_t *format, va_list ap);
extern uint16_t *
       u16_u16_vasnprintf (uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp,
                           const uint16_t *format, va_list ap);

/* ASCII format string, result in UTF-32 format.  */
extern int
       u32_sprintf (uint32_t *buf,
                    const char *format, ...);
extern int
       u32_snprintf (uint32_t *buf, size_t size,
                     const char *format, ...);
extern int
       u32_asprintf (uint32_t **resultp,
                     const char *format, ...);
extern uint32_t *
       u32_asnprintf (uint32_t *resultbuf, size_t *lengthp,
                      const char *format, ...);
extern int
       u32_vsprintf (uint32_t *buf,
                     const char *format, va_list ap);
extern int
       u32_vsnprintf (uint32_t *buf, size_t size,
                      const char *format, va_list ap);
extern int
       u32_vasprintf (uint32_t **resultp,
                      const char *format, va_list ap);
extern uint32_t *
       u32_vasnprintf (uint32_t *resultbuf, size_t *lengthp,
                       const char *format, va_list ap);

/* UTF-32 format string, result in UTF-32 format.  */
extern int
       u32_u32_sprintf (uint32_t *_UC_RESTRICT buf,
                        const uint32_t *format, ...);
extern int
       u32_u32_snprintf (uint32_t *_UC_RESTRICT buf, size_t size,
                         const uint32_t *format, ...);
extern int
       u32_u32_asprintf (uint32_t **resultp,
                         const uint32_t *format, ...);
extern uint32_t *
       u32_u32_asnprintf (uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp,
                          const uint32_t *format, ...);
extern int
       u32_u32_vsprintf (uint32_t *_UC_RESTRICT buf,
                         const uint32_t *format, va_list ap);
extern int
       u32_u32_vsnprintf (uint32_t *_UC_RESTRICT buf, size_t size,
                          const uint32_t *format, va_list ap);
extern int
       u32_u32_vasprintf (uint32_t **resultp,
                          const uint32_t *format, va_list ap);
extern uint32_t *
       u32_u32_vasnprintf (uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp,
                           const uint32_t *format, va_list ap);

/* ASCII format string, output to FILE in locale dependent encoding.  */
extern int
       ulc_fprintf (FILE *stream,
                    const char *format, ...);
extern int
       ulc_vfprintf (FILE *stream,
                     const char *format, va_list ap);

#ifdef __cplusplus
}
#endif

#endif /* _UNISTDIO_H */
