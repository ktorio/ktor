/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Normalization forms (composition and decomposition) of Unicode strings.
   Copyright (C) 2001-2002, 2009-2022 Free Software Foundation, Inc.
   Written by Bruno Haible <bruno@clisp.org>, 2009.

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

#ifndef _UNINORM_H
#define _UNINORM_H

/* Get common macros for C.  */
#include <unistring/cdefs.h>

/* Get LIBUNISTRING_DLL_VARIABLE.  */
#include <unistring/woe32dll.h>

/* Get size_t.  */
#include <stddef.h>

#include "unitypes.h"


#ifdef __cplusplus
extern "C" {
#endif


/* Conventions:

   All functions prefixed with u8_ operate on UTF-8 encoded strings.
   Their unit is an uint8_t (1 byte).

   All functions prefixed with u16_ operate on UTF-16 encoded strings.
   Their unit is an uint16_t (a 2-byte word).

   All functions prefixed with u32_ operate on UCS-4 encoded strings.
   Their unit is an uint32_t (a 4-byte word).

   All argument pairs (s, n) denote a Unicode string s[0..n-1] with exactly
   n units.

   Functions returning a string result take a (resultbuf, lengthp) argument
   pair.  If resultbuf is not NULL and the result fits into *lengthp units,
   it is put in resultbuf, and resultbuf is returned.  Otherwise, a freshly
   allocated string is returned.  In both cases, *lengthp is set to the
   length (number of units) of the returned string.  In case of error,
   NULL is returned and errno is set.  */


enum
{
  UC_DECOMP_CANONICAL,/*            Canonical decomposition.                  */
  UC_DECOMP_FONT,    /*   <font>    A font variant (e.g. a blackletter form). */
  UC_DECOMP_NOBREAK, /* <noBreak>   A no-break version of a space or hyphen.  */
  UC_DECOMP_INITIAL, /* <initial>   An initial presentation form (Arabic).    */
  UC_DECOMP_MEDIAL,  /*  <medial>   A medial presentation form (Arabic).      */
  UC_DECOMP_FINAL,   /*  <final>    A final presentation form (Arabic).       */
  UC_DECOMP_ISOLATED,/* <isolated>  An isolated presentation form (Arabic).   */
  UC_DECOMP_CIRCLE,  /*  <circle>   An encircled form.                        */
  UC_DECOMP_SUPER,   /*  <super>    A superscript form.                       */
  UC_DECOMP_SUB,     /*   <sub>     A subscript form.                         */
  UC_DECOMP_VERTICAL,/* <vertical>  A vertical layout presentation form.      */
  UC_DECOMP_WIDE,    /*   <wide>    A wide (or zenkaku) compatibility character. */
  UC_DECOMP_NARROW,  /*  <narrow>   A narrow (or hankaku) compatibility character. */
  UC_DECOMP_SMALL,   /*  <small>    A small variant form (CNS compatibility). */
  UC_DECOMP_SQUARE,  /*  <square>   A CJK squared font variant.               */
  UC_DECOMP_FRACTION,/* <fraction>  A vulgar fraction form.                   */
  UC_DECOMP_COMPAT   /*  <compat>   Otherwise unspecified compatibility character. */
};

/* Maximum size of decomposition of a single Unicode character.  */
#define UC_DECOMPOSITION_MAX_LENGTH 32

/* Return the character decomposition mapping of a Unicode character.
   DECOMPOSITION must point to an array of at least UC_DECOMPOSITION_MAX_LENGTH
   ucs_t elements.
   When a decomposition exists, DECOMPOSITION[0..N-1] and *DECOMP_TAG are
   filled and N is returned.  Otherwise -1 is returned.  */
extern int
       uc_decomposition (ucs4_t uc, int *decomp_tag, ucs4_t *decomposition);

/* Return the canonical character decomposition mapping of a Unicode character.
   DECOMPOSITION must point to an array of at least UC_DECOMPOSITION_MAX_LENGTH
   ucs_t elements.
   When a decomposition exists, DECOMPOSITION[0..N-1] is filled and N is
   returned.  Otherwise -1 is returned.  */
extern int
       uc_canonical_decomposition (ucs4_t uc, ucs4_t *decomposition);


/* Attempt to combine the Unicode characters uc1, uc2.
   uc1 is known to have canonical combining class 0.
   Return the combination of uc1 and uc2, if it exists.
   Return 0 otherwise.
   Not all decompositions can be recombined using this function.  See the
   Unicode file CompositionExclusions.txt for details.  */
extern ucs4_t
       uc_composition (ucs4_t uc1, ucs4_t uc2)
       _UC_ATTRIBUTE_CONST;


/* An object of type uninorm_t denotes a Unicode normalization form.  */
struct unicode_normalization_form;
typedef const struct unicode_normalization_form *uninorm_t;

/* UNINORM_NFD: Normalization form D: canonical decomposition.  */
extern LIBUNISTRING_DLL_VARIABLE const struct unicode_normalization_form uninorm_nfd;
#define UNINORM_NFD (&uninorm_nfd)

/* UNINORM_NFC: Normalization form C: canonical decomposition, then
   canonical composition.  */
extern LIBUNISTRING_DLL_VARIABLE const struct unicode_normalization_form uninorm_nfc;
#define UNINORM_NFC (&uninorm_nfc)

/* UNINORM_NFKD: Normalization form KD: compatibility decomposition.  */
extern LIBUNISTRING_DLL_VARIABLE const struct unicode_normalization_form uninorm_nfkd;
#define UNINORM_NFKD (&uninorm_nfkd)

/* UNINORM_NFKC: Normalization form KC: compatibility decomposition, then
   canonical composition.  */
extern LIBUNISTRING_DLL_VARIABLE const struct unicode_normalization_form uninorm_nfkc;
#define UNINORM_NFKC (&uninorm_nfkc)

/* Test whether a normalization form does compatibility decomposition.  */
#define uninorm_is_compat_decomposing(nf) \
  ((* (const unsigned int *) (nf) >> 0) & 1)

/* Test whether a normalization form includes canonical composition.  */
#define uninorm_is_composing(nf) \
  ((* (const unsigned int *) (nf) >> 1) & 1)

/* Return the decomposing variant of a normalization form.
   This maps NFC,NFD -> NFD and NFKC,NFKD -> NFKD.  */
extern uninorm_t
       uninorm_decomposing_form (uninorm_t nf)
       _UC_ATTRIBUTE_PURE;


/* Return the specified normalization form of a string.  */
extern uint8_t *
       u8_normalize (uninorm_t nf, const uint8_t *s, size_t n,
                     uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_normalize (uninorm_t nf, const uint16_t *s, size_t n,
                      uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_normalize (uninorm_t nf, const uint32_t *s, size_t n,
                      uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);


/* Compare S1 and S2, ignoring differences in normalization.
   NF must be either UNINORM_NFD or UNINORM_NFKD.
   If successful, set *RESULTP to -1 if S1 < S2, 0 if S1 = S2, 1 if S1 > S2, and
   return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_normcmp (const uint8_t *s1, size_t n1, const uint8_t *s2, size_t n2,
                   uninorm_t nf, int *resultp);
extern int
       u16_normcmp (const uint16_t *s1, size_t n1, const uint16_t *s2, size_t n2,
                    uninorm_t nf, int *resultp);
extern int
       u32_normcmp (const uint32_t *s1, size_t n1, const uint32_t *s2, size_t n2,
                    uninorm_t nf, int *resultp);


/* Converts the string S of length N to a NUL-terminated byte sequence, in such
   a way that comparing uN_normxfrm (S1) and uN_normxfrm (S2) with uN_cmp2() is
   equivalent to comparing S1 and S2 with uN_normcoll().
   NF must be either UNINORM_NFC or UNINORM_NFKC.  */
extern char *
       u8_normxfrm (const uint8_t *s, size_t n, uninorm_t nf,
                    char *resultbuf, size_t *lengthp);
extern char *
       u16_normxfrm (const uint16_t *s, size_t n, uninorm_t nf,
                     char *resultbuf, size_t *lengthp);
extern char *
       u32_normxfrm (const uint32_t *s, size_t n, uninorm_t nf,
                     char *resultbuf, size_t *lengthp);


/* Compare S1 and S2, ignoring differences in normalization, using the
   collation rules of the current locale.
   NF must be either UNINORM_NFC or UNINORM_NFKC.
   If successful, set *RESULTP to -1 if S1 < S2, 0 if S1 = S2, 1 if S1 > S2, and
   return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_normcoll (const uint8_t *s1, size_t n1, const uint8_t *s2, size_t n2,
                    uninorm_t nf, int *resultp);
extern int
       u16_normcoll (const uint16_t *s1, size_t n1, const uint16_t *s2, size_t n2,
                     uninorm_t nf, int *resultp);
extern int
       u32_normcoll (const uint32_t *s1, size_t n1, const uint32_t *s2, size_t n2,
                     uninorm_t nf, int *resultp);


/* Normalization of a stream of Unicode characters.

   A "stream of Unicode characters" is essentially a function that accepts an
   ucs4_t argument repeatedly, optionally combined with a function that
   "flushes" the stream.  */

/* Data type of a stream of Unicode characters that normalizes its input
   according to a given normalization form and passes the normalized character
   sequence to the encapsulated stream of Unicode characters.  */
struct uninorm_filter;

/* Bring data buffered in the filter to its destination, the encapsulated
   stream, then close and free the filter.
   Return 0 if successful, or -1 with errno set upon failure.  */
extern int
       uninorm_filter_free (struct uninorm_filter *filter);

/* Create and return a normalization filter for Unicode characters.
   The pair (stream_func, stream_data) is the encapsulated stream.
   stream_func (stream_data, uc) receives the Unicode character uc
   and returns 0 if successful, or -1 with errno set upon failure.
   Return the new filter, or NULL with errno set upon failure.  */
extern struct uninorm_filter *
       uninorm_filter_create (uninorm_t nf,
                              int (*stream_func) (void *stream_data, ucs4_t uc),
                              void *stream_data)
       _GL_ATTRIBUTE_DEALLOC (uninorm_filter_free, 1);

/* Stuff a Unicode character into a normalizing filter.
   Return 0 if successful, or -1 with errno set upon failure.  */
extern int
       uninorm_filter_write (struct uninorm_filter *filter, ucs4_t uc);

/* Bring data buffered in the filter to its destination, the encapsulated
   stream.
   Return 0 if successful, or -1 with errno set upon failure.
   Note! If after calling this function, additional characters are written
   into the filter, the resulting character sequence in the encapsulated stream
   will not necessarily be normalized.  */
extern int
       uninorm_filter_flush (struct uninorm_filter *filter);


#ifdef __cplusplus
}
#endif


#endif /* _UNINORM_H */
