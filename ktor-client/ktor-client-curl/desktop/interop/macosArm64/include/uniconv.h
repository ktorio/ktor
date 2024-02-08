/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Conversions between Unicode and legacy encodings.
   Copyright (C) 2002, 2005, 2007, 2009-2022 Free Software Foundation, Inc.

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

#ifndef _UNICONV_H
#define _UNICONV_H

/* Get size_t.  */
#include <stddef.h>

#include "unitypes.h"

/* Get enum iconv_ilseq_handler.  */
#include <unistring/iconveh.h>

/* Get locale_charset() declaration.  */
#include <unistring/localcharset.h>


#ifdef __cplusplus
extern "C" {
#endif


/* Converts an entire string, possibly including NUL bytes, from one encoding
   to a Unicode encoding.
   Converts a memory region given in encoding FROMCODE.  FROMCODE is as for
   iconv_open(3).
   The input is in the memory region between SRC (inclusive) and SRC + SRCLEN
   (exclusive).
   If OFFSETS is not NULL, it should point to an array of SRCLEN integers; this
   array is filled with offsets into the result, i.e. the character starting
   at SRC[i] corresponds to the character starting at (*RESULTP)[OFFSETS[i]],
   and other offsets are set to (size_t)(-1).
   RESULTBUF and *LENGTHP should initially be a scratch buffer and its size,
   or *RESULTBUF can be NULL.
   May erase the contents of the memory at RESULTBUF.
   If successful: The resulting Unicode string (non-NULL) is returned and its
   length stored in *LENGTHP.  The resulting string is RESULTBUF if no dynamic
   memory allocation was necessary, or a freshly allocated memory block
   otherwise.
   In case of error: NULL is returned and errno is set.  Particular errno
   values: EINVAL, EILSEQ, ENOMEM.  */
extern uint8_t *
       u8_conv_from_encoding (const char *fromcode,
                              enum iconv_ilseq_handler handler,
                              const char *src, size_t srclen,
                              size_t *offsets,
                              uint8_t *resultbuf, size_t *lengthp);
extern uint16_t *
       u16_conv_from_encoding (const char *fromcode,
                               enum iconv_ilseq_handler handler,
                               const char *src, size_t srclen,
                               size_t *offsets,
                               uint16_t *resultbuf, size_t *lengthp);
extern uint32_t *
       u32_conv_from_encoding (const char *fromcode,
                               enum iconv_ilseq_handler handler,
                               const char *src, size_t srclen,
                               size_t *offsets,
                               uint32_t *resultbuf, size_t *lengthp);

/* Converts an entire Unicode string, possibly including NUL units, from a
   Unicode encoding to a given encoding.
   Converts a memory region to encoding TOCODE.  TOCODE is as for
   iconv_open(3).
   The input is in the memory region between SRC (inclusive) and SRC + SRCLEN
   (exclusive).
   If OFFSETS is not NULL, it should point to an array of SRCLEN integers; this
   array is filled with offsets into the result, i.e. the character starting
   at SRC[i] corresponds to the character starting at (*RESULTP)[OFFSETS[i]],
   and other offsets are set to (size_t)(-1).
   RESULTBUF and *LENGTHP should initially be a scratch buffer and its size,
   or RESULTBUF can be NULL.
   May erase the contents of the memory at RESULTBUF.
   If successful: The resulting string (non-NULL) is returned and its length
   stored in *LENGTHP.  The resulting string is RESULTBUF if no dynamic memory
   allocation was necessary, or a freshly allocated memory block otherwise.
   In case of error: NULL is returned and errno is set.  Particular errno
   values: EINVAL, EILSEQ, ENOMEM.  */
extern char *
       u8_conv_to_encoding (const char *tocode,
                            enum iconv_ilseq_handler handler,
                            const uint8_t *src, size_t srclen,
                            size_t *offsets,
                            char *_UC_RESTRICT resultbuf, size_t *lengthp);
extern char *
       u16_conv_to_encoding (const char *tocode,
                             enum iconv_ilseq_handler handler,
                             const uint16_t *src, size_t srclen,
                             size_t *offsets,
                             char *_UC_RESTRICT resultbuf, size_t *lengthp);
extern char *
       u32_conv_to_encoding (const char *tocode,
                             enum iconv_ilseq_handler handler,
                             const uint32_t *src, size_t srclen,
                             size_t *offsets,
                             char *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Converts a NUL terminated string from a given encoding.
   The result is malloc allocated, or NULL (with errno set) in case of error.
   Particular errno values: EILSEQ, ENOMEM.  */
extern uint8_t *
       u8_strconv_from_encoding (const char *string,
                                 const char *fromcode,
                                 enum iconv_ilseq_handler handler);
extern uint16_t *
       u16_strconv_from_encoding (const char *string,
                                  const char *fromcode,
                                  enum iconv_ilseq_handler handler);
extern uint32_t *
       u32_strconv_from_encoding (const char *string,
                                  const char *fromcode,
                                  enum iconv_ilseq_handler handler);

/* Converts a NUL terminated string to a given encoding.
   The result is malloc allocated, or NULL (with errno set) in case of error.
   Particular errno values: EILSEQ, ENOMEM.  */
extern char *
       u8_strconv_to_encoding (const uint8_t *string,
                               const char *tocode,
                               enum iconv_ilseq_handler handler);
extern char *
       u16_strconv_to_encoding (const uint16_t *string,
                                const char *tocode,
                                enum iconv_ilseq_handler handler);
extern char *
       u32_strconv_to_encoding (const uint32_t *string,
                                const char *tocode,
                                enum iconv_ilseq_handler handler);

/* Converts a NUL terminated string from the locale encoding.
   The result is malloc allocated, or NULL (with errno set) in case of error.
   Particular errno values: ENOMEM.  */
extern uint8_t *
       u8_strconv_from_locale (const char *string);
extern uint16_t *
       u16_strconv_from_locale (const char *string);
extern uint32_t *
       u32_strconv_from_locale (const char *string);

/* Converts a NUL terminated string to the locale encoding.
   The result is malloc allocated, or NULL (with errno set) in case of error.
   Particular errno values: ENOMEM.  */
extern char *
       u8_strconv_to_locale (const uint8_t *string);
extern char *
       u16_strconv_to_locale (const uint16_t *string);
extern char *
       u32_strconv_to_locale (const uint32_t *string);


#ifdef __cplusplus
}
#endif

#endif /* _UNICONV_H */
