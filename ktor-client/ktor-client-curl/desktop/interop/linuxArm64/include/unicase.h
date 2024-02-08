/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Unicode character case mappings.
   Copyright (C) 2002, 2009-2022 Free Software Foundation, Inc.

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

#ifndef _UNICASE_H
#define _UNICASE_H

#include "unitypes.h"

/* Get bool.  */
#include <unistring/stdbool.h>

/* Get size_t.  */
#include <stddef.h>

/* Get uninorm_t.  */
#include "uninorm.h"

#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================= */

/* Character case mappings.
   These mappings are locale and context independent.
   WARNING! These functions are not sufficient for languages such as German.
   Better use the functions below that treat an entire string at once and are
   language aware.  */

/* Return the uppercase mapping of a Unicode character.  */
extern ucs4_t
       uc_toupper (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Return the lowercase mapping of a Unicode character.  */
extern ucs4_t
       uc_tolower (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Return the titlecase mapping of a Unicode character.  */
extern ucs4_t
       uc_totitle (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* String case mappings.  */

/* These functions are locale dependent.  The iso639_language argument
   identifies the language (e.g. "tr" for Turkish).  NULL means to use
   locale independent case mappings.  */

/* Return the ISO 639 language code of the current locale.
   Return "" if it is unknown, or in the "C" locale.  */
extern const char *
       uc_locale_language (void)
       _UC_ATTRIBUTE_PURE;

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

/* Return the uppercase mapping of a string.
   The nf argument identifies the normalization form to apply after the
   case-mapping.  It can also be NULL, for no normalization.  */
extern uint8_t *
       u8_toupper (const uint8_t *s, size_t n, const char *iso639_language,
                   uninorm_t nf,
                   uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_toupper (const uint16_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_toupper (const uint32_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Return the lowercase mapping of a string.
   The nf argument identifies the normalization form to apply after the
   case-mapping.  It can also be NULL, for no normalization.  */
extern uint8_t *
       u8_tolower (const uint8_t *s, size_t n, const char *iso639_language,
                   uninorm_t nf,
                   uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_tolower (const uint16_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_tolower (const uint32_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Return the titlecase mapping of a string.
   The nf argument identifies the normalization form to apply after the
   case-mapping.  It can also be NULL, for no normalization.  */
extern uint8_t *
       u8_totitle (const uint8_t *s, size_t n, const char *iso639_language,
                   uninorm_t nf,
                   uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_totitle (const uint16_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_totitle (const uint32_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* The case-mapping context given by a prefix string.  */
typedef struct casing_prefix_context
        {
          /* These fields are private, undocumented.  */
          uint32_t last_char_except_ignorable;
          uint32_t last_char_normal_or_above;
        }
        casing_prefix_context_t;
/* The case-mapping context of the empty prefix string.  */
extern LIBUNISTRING_DLL_VARIABLE const casing_prefix_context_t unicase_empty_prefix_context;
/* Return the case-mapping context of a given prefix string.  */
extern casing_prefix_context_t
       u8_casing_prefix_context (const uint8_t *s, size_t n);
extern casing_prefix_context_t
       u16_casing_prefix_context (const uint16_t *s, size_t n);
extern casing_prefix_context_t
       u32_casing_prefix_context (const uint32_t *s, size_t n);
/* Return the case-mapping context of the prefix concat(A, S), given the
   case-mapping context of the prefix A.  */
extern casing_prefix_context_t
       u8_casing_prefixes_context (const uint8_t *s, size_t n,
                                   casing_prefix_context_t a_context);
extern casing_prefix_context_t
       u16_casing_prefixes_context (const uint16_t *s, size_t n,
                                    casing_prefix_context_t a_context);
extern casing_prefix_context_t
       u32_casing_prefixes_context (const uint32_t *s, size_t n,
                                    casing_prefix_context_t a_context);

/* The case-mapping context given by a suffix string.  */
typedef struct casing_suffix_context
        {
          /* These fields are private, undocumented.  */
          uint32_t first_char_except_ignorable;
          uint32_t bits;
        }
        casing_suffix_context_t;
/* The case-mapping context of the empty suffix string.  */
extern LIBUNISTRING_DLL_VARIABLE const casing_suffix_context_t unicase_empty_suffix_context;
/* Return the case-mapping context of a given suffix string.  */
extern casing_suffix_context_t
       u8_casing_suffix_context (const uint8_t *s, size_t n);
extern casing_suffix_context_t
       u16_casing_suffix_context (const uint16_t *s, size_t n);
extern casing_suffix_context_t
       u32_casing_suffix_context (const uint32_t *s, size_t n);
/* Return the case-mapping context of the suffix concat(S, A), given the
   case-mapping context of the suffix A.  */
extern casing_suffix_context_t
       u8_casing_suffixes_context (const uint8_t *s, size_t n,
                                   casing_suffix_context_t a_context);
extern casing_suffix_context_t
       u16_casing_suffixes_context (const uint16_t *s, size_t n,
                                    casing_suffix_context_t a_context);
extern casing_suffix_context_t
       u32_casing_suffixes_context (const uint32_t *s, size_t n,
                                    casing_suffix_context_t a_context);

/* Return the uppercase mapping of a string that is surrounded by a prefix
   and a suffix.  */
extern uint8_t *
       u8_ct_toupper (const uint8_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_ct_toupper (const uint16_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_ct_toupper (const uint32_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Return the lowercase mapping of a string that is surrounded by a prefix
   and a suffix.  */
extern uint8_t *
       u8_ct_tolower (const uint8_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_ct_tolower (const uint16_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_ct_tolower (const uint32_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Return the titlecase mapping of a string that is surrounded by a prefix
   and a suffix.  */
extern uint8_t *
       u8_ct_totitle (const uint8_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_ct_totitle (const uint16_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_ct_totitle (const uint32_t *s, size_t n,
                      casing_prefix_context_t prefix_context,
                      casing_suffix_context_t suffix_context,
                      const char *iso639_language,
                      uninorm_t nf,
                      uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Return the case folded string.
   Comparing uN_casefold (S1) and uN_casefold (S2) with uN_cmp2() is equivalent
   to comparing S1 and S2 with uN_casecmp().
   The nf argument identifies the normalization form to apply after the
   case-mapping.  It can also be NULL, for no normalization.  */
extern uint8_t *
       u8_casefold (const uint8_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_casefold (const uint16_t *s, size_t n, const char *iso639_language,
                     uninorm_t nf,
                     uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_casefold (const uint32_t *s, size_t n, const char *iso639_language,
                     uninorm_t nf,
                     uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);
/* Likewise, for a string that is surrounded by a prefix and a suffix.  */
extern uint8_t *
       u8_ct_casefold (const uint8_t *s, size_t n,
                       casing_prefix_context_t prefix_context,
                       casing_suffix_context_t suffix_context,
                       const char *iso639_language,
                       uninorm_t nf,
                       uint8_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint16_t *
       u16_ct_casefold (const uint16_t *s, size_t n,
                        casing_prefix_context_t prefix_context,
                        casing_suffix_context_t suffix_context,
                        const char *iso639_language,
                        uninorm_t nf,
                        uint16_t *_UC_RESTRICT resultbuf, size_t *lengthp);
extern uint32_t *
       u32_ct_casefold (const uint32_t *s, size_t n,
                        casing_prefix_context_t prefix_context,
                        casing_suffix_context_t suffix_context,
                        const char *iso639_language,
                        uninorm_t nf,
                        uint32_t *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Compare S1 and S2, ignoring differences in case and normalization.
   The nf argument identifies the normalization form to apply after the
   case-mapping.  It can also be NULL, for no normalization.
   If successful, set *RESULTP to -1 if S1 < S2, 0 if S1 = S2, 1 if S1 > S2, and
   return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_casecmp (const uint8_t *s1, size_t n1,
                   const uint8_t *s2, size_t n2,
                   const char *iso639_language, uninorm_t nf, int *resultp);
extern int
       u16_casecmp (const uint16_t *s1, size_t n1,
                    const uint16_t *s2, size_t n2,
                    const char *iso639_language, uninorm_t nf, int *resultp);
extern int
       u32_casecmp (const uint32_t *s1, size_t n1,
                    const uint32_t *s2, size_t n2,
                    const char *iso639_language, uninorm_t nf, int *resultp);
extern int
       ulc_casecmp (const char *s1, size_t n1,
                    const char *s2, size_t n2,
                    const char *iso639_language, uninorm_t nf, int *resultp);

/* Convert the string S of length N to a NUL-terminated byte sequence, in such
   a way that comparing uN_casexfrm (S1) and uN_casexfrm (S2) with the gnulib
   function memcmp2() is equivalent to comparing S1 and S2 with uN_casecoll().
   NF must be either UNINORM_NFC, UNINORM_NFKC, or NULL for no normalization.  */
extern char *
       u8_casexfrm (const uint8_t *s, size_t n, const char *iso639_language,
                    uninorm_t nf,
                    char *_UC_RESTRICT resultbuf, size_t *lengthp);
extern char *
       u16_casexfrm (const uint16_t *s, size_t n, const char *iso639_language,
                     uninorm_t nf,
                     char *_UC_RESTRICT resultbuf, size_t *lengthp);
extern char *
       u32_casexfrm (const uint32_t *s, size_t n, const char *iso639_language,
                     uninorm_t nf,
                     char *_UC_RESTRICT resultbuf, size_t *lengthp);
extern char *
       ulc_casexfrm (const char *s, size_t n, const char *iso639_language,
                     uninorm_t nf,
                     char *_UC_RESTRICT resultbuf, size_t *lengthp);

/* Compare S1 and S2, ignoring differences in case and normalization, using the
   collation rules of the current locale.
   The nf argument identifies the normalization form to apply after the
   case-mapping.  It must be either UNINORM_NFC or UNINORM_NFKC.  It can also
   be NULL, for no normalization.
   If successful, set *RESULTP to -1 if S1 < S2, 0 if S1 = S2, 1 if S1 > S2, and
   return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_casecoll (const uint8_t *s1, size_t n1,
                    const uint8_t *s2, size_t n2,
                    const char *iso639_language, uninorm_t nf, int *resultp);
extern int
       u16_casecoll (const uint16_t *s1, size_t n1,
                     const uint16_t *s2, size_t n2,
                     const char *iso639_language, uninorm_t nf, int *resultp);
extern int
       u32_casecoll (const uint32_t *s1, size_t n1,
                     const uint32_t *s2, size_t n2,
                     const char *iso639_language, uninorm_t nf, int *resultp);
extern int
       ulc_casecoll (const char *s1, size_t n1,
                     const char *s2, size_t n2,
                     const char *iso639_language, uninorm_t nf, int *resultp);


/* Set *RESULTP to true if mapping NFD(S) to upper case is a no-op, or to false
   otherwise, and return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_is_uppercase (const uint8_t *s, size_t n,
                        const char *iso639_language,
                        bool *resultp);
extern int
       u16_is_uppercase (const uint16_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);
extern int
       u32_is_uppercase (const uint32_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);

/* Set *RESULTP to true if mapping NFD(S) to lower case is a no-op, or to false
   otherwise, and return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_is_lowercase (const uint8_t *s, size_t n,
                        const char *iso639_language,
                        bool *resultp);
extern int
       u16_is_lowercase (const uint16_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);
extern int
       u32_is_lowercase (const uint32_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);

/* Set *RESULTP to true if mapping NFD(S) to title case is a no-op, or to false
   otherwise, and return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_is_titlecase (const uint8_t *s, size_t n,
                        const char *iso639_language,
                        bool *resultp);
extern int
       u16_is_titlecase (const uint16_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);
extern int
       u32_is_titlecase (const uint32_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);

/* Set *RESULTP to true if applying case folding to NFD(S) is a no-op, or to
   false otherwise, and return 0.  Upon failure, return -1 with errno set.  */
extern int
       u8_is_casefolded (const uint8_t *s, size_t n,
                         const char *iso639_language,
                         bool *resultp);
extern int
       u16_is_casefolded (const uint16_t *s, size_t n,
                          const char *iso639_language,
                          bool *resultp);
extern int
       u32_is_casefolded (const uint32_t *s, size_t n,
                          const char *iso639_language,
                          bool *resultp);

/* Set *RESULTP to true if case matters for S, that is, if mapping NFD(S) to
   either upper case or lower case or title case is not a no-op.
   Set *RESULTP to false if NFD(S) maps to itself under the upper case mapping,
   under the lower case mapping, and under the title case mapping; in other
   words, when NFD(S) consists entirely of caseless characters.
   Upon failure, return -1 with errno set.  */
extern int
       u8_is_cased (const uint8_t *s, size_t n,
                    const char *iso639_language,
                    bool *resultp);
extern int
       u16_is_cased (const uint16_t *s, size_t n,
                     const char *iso639_language,
                     bool *resultp);
extern int
       u32_is_cased (const uint32_t *s, size_t n,
                     const char *iso639_language,
                     bool *resultp);


/* ========================================================================= */

#ifdef __cplusplus
}
#endif

#endif /* _UNICASE_H */
