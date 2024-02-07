/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Word breaks in Unicode strings.
   Copyright (C) 2001-2003, 2005-2024 Free Software Foundation, Inc.
   Written by Bruno Haible <bruno@clisp.org>, 2009.

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

#ifndef _UNIWBRK_H
#define _UNIWBRK_H

/* Get size_t.  */
#include <stddef.h>

#include "unitypes.h"


#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================= */

/* Property defined in Unicode Standard Annex #29, section "Word Boundaries"
   <https://www.unicode.org/reports/tr29/#Word_Boundaries>  */

/* Possible values of the Word_Break property.
   This enumeration may be extended in the future.  */
enum
{
  WBP_OTHER        = 0,
  WBP_CR           = 11,
  WBP_LF           = 12,
  WBP_NEWLINE      = 10,
  WBP_EXTEND       = 8,
  WBP_FORMAT       = 9,
  WBP_KATAKANA     = 1,
  WBP_ALETTER      = 2,
  WBP_MIDNUMLET    = 3,
  WBP_MIDLETTER    = 4,
  WBP_MIDNUM       = 5,
  WBP_NUMERIC      = 6,
  WBP_EXTENDNUMLET = 7,
  WBP_RI           = 13,
  WBP_DQ           = 14,
  WBP_SQ           = 15,
  WBP_HL           = 16,
  WBP_ZWJ          = 17,
  WBP_EB           = 18, /* obsolete */
  WBP_EM           = 19, /* obsolete */
  WBP_GAZ          = 20, /* obsolete */
  WBP_EBG          = 21, /* obsolete */
  WBP_WSS          = 22
};

/* Return the Word_Break property of a Unicode character.  */
extern int
       uc_wordbreak_property (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Word breaks.  */

/* Determine the word break points in S, and store the result at p[0..n-1].
   p[i] = 1 means that there is a word boundary between s[i-1] and s[i].
   p[i] = 0 means that s[i-1] and s[i] must not be separated.
 */
extern void
       u8_wordbreaks (const uint8_t *s, size_t n, char *p);
extern void
       u16_wordbreaks (const uint16_t *s, size_t n, char *p);
extern void
       u32_wordbreaks (const uint32_t *s, size_t n, char *p);
extern void
       ulc_wordbreaks (const char *s, size_t n, char *_UC_RESTRICT p);

/* ========================================================================= */

#ifdef __cplusplus
}
#endif


#endif /* _UNIWBRK_H */
