/* DO NOT EDIT! GENERATED AUTOMATICALLY! */
/* Unicode character classification and properties.
   Copyright (C) 2002, 2005-2022 Free Software Foundation, Inc.

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

#ifndef _UNICTYPE_H
#define _UNICTYPE_H

#include "unitypes.h"

/* Get LIBUNISTRING_DLL_VARIABLE.  */
#include <unistring/woe32dll.h>

/* Get bool.  */
#include <unistring/stdbool.h>

/* Get size_t.  */
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ========================================================================= */

/* Field 1 of Unicode Character Database: Character name.
   See "uniname.h".  */

/* ========================================================================= */

/* Field 2 of Unicode Character Database: General category.  */

/* Data type denoting a General category value.  This is not just a bitmask,
   but rather a bitmask and a pointer to the lookup table, so that programs
   that use only the predefined bitmasks (i.e. don't combine bitmasks with &
   and |) don't have a link-time dependency towards the big general table.  */
typedef struct
{
  uint32_t bitmask : 31;
  /*bool*/ unsigned int generic : 1;
  union
  {
    const void *table;                               /* when generic is 0 */
    bool (*lookup_fn) (ucs4_t uc, uint32_t bitmask); /* when generic is 1 */
  } lookup;
}
uc_general_category_t;

/* Bits and bit masks denoting General category values.  UnicodeData-3.2.0.html
   says a 32-bit integer will always suffice to represent them.
   These bit masks can only be used with the uc_is_general_category_withtable
   function.  */
enum
{
  UC_CATEGORY_MASK_L  = 0x0000001f,
  UC_CATEGORY_MASK_LC = 0x00000007,
  UC_CATEGORY_MASK_Lu = 0x00000001,
  UC_CATEGORY_MASK_Ll = 0x00000002,
  UC_CATEGORY_MASK_Lt = 0x00000004,
  UC_CATEGORY_MASK_Lm = 0x00000008,
  UC_CATEGORY_MASK_Lo = 0x00000010,
  UC_CATEGORY_MASK_M  = 0x000000e0,
  UC_CATEGORY_MASK_Mn = 0x00000020,
  UC_CATEGORY_MASK_Mc = 0x00000040,
  UC_CATEGORY_MASK_Me = 0x00000080,
  UC_CATEGORY_MASK_N  = 0x00000700,
  UC_CATEGORY_MASK_Nd = 0x00000100,
  UC_CATEGORY_MASK_Nl = 0x00000200,
  UC_CATEGORY_MASK_No = 0x00000400,
  UC_CATEGORY_MASK_P  = 0x0003f800,
  UC_CATEGORY_MASK_Pc = 0x00000800,
  UC_CATEGORY_MASK_Pd = 0x00001000,
  UC_CATEGORY_MASK_Ps = 0x00002000,
  UC_CATEGORY_MASK_Pe = 0x00004000,
  UC_CATEGORY_MASK_Pi = 0x00008000,
  UC_CATEGORY_MASK_Pf = 0x00010000,
  UC_CATEGORY_MASK_Po = 0x00020000,
  UC_CATEGORY_MASK_S  = 0x003c0000,
  UC_CATEGORY_MASK_Sm = 0x00040000,
  UC_CATEGORY_MASK_Sc = 0x00080000,
  UC_CATEGORY_MASK_Sk = 0x00100000,
  UC_CATEGORY_MASK_So = 0x00200000,
  UC_CATEGORY_MASK_Z  = 0x01c00000,
  UC_CATEGORY_MASK_Zs = 0x00400000,
  UC_CATEGORY_MASK_Zl = 0x00800000,
  UC_CATEGORY_MASK_Zp = 0x01000000,
  UC_CATEGORY_MASK_C  = 0x3e000000,
  UC_CATEGORY_MASK_Cc = 0x02000000,
  UC_CATEGORY_MASK_Cf = 0x04000000,
  UC_CATEGORY_MASK_Cs = 0x08000000,
  UC_CATEGORY_MASK_Co = 0x10000000,
  UC_CATEGORY_MASK_Cn = 0x20000000
};

/* Predefined General category values.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_L;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_LC;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Lu;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Ll;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Lt;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Lm;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Lo;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_M;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Mn;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Mc;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Me;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_N;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Nd;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Nl;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_No;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_P;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Pc;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Pd;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Ps;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Pe;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Pi;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Pf;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Po;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_S;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Sm;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Sc;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Sk;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_So;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Z;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Zs;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Zl;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Zp;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_C;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Cc;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Cf;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Cs;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Co;
extern LIBUNISTRING_DLL_VARIABLE const uc_general_category_t UC_CATEGORY_Cn;
/* Non-public.  */
extern const uc_general_category_t _UC_CATEGORY_NONE;

/* Alias names for predefined General category values.  */
#define UC_LETTER                    UC_CATEGORY_L
#define UC_CASED_LETTER              UC_CATEGORY_LC
#define UC_UPPERCASE_LETTER          UC_CATEGORY_Lu
#define UC_LOWERCASE_LETTER          UC_CATEGORY_Ll
#define UC_TITLECASE_LETTER          UC_CATEGORY_Lt
#define UC_MODIFIER_LETTER           UC_CATEGORY_Lm
#define UC_OTHER_LETTER              UC_CATEGORY_Lo
#define UC_MARK                      UC_CATEGORY_M
#define UC_NON_SPACING_MARK          UC_CATEGORY_Mn
#define UC_COMBINING_SPACING_MARK    UC_CATEGORY_Mc
#define UC_ENCLOSING_MARK            UC_CATEGORY_Me
#define UC_NUMBER                    UC_CATEGORY_N
#define UC_DECIMAL_DIGIT_NUMBER      UC_CATEGORY_Nd
#define UC_LETTER_NUMBER             UC_CATEGORY_Nl
#define UC_OTHER_NUMBER              UC_CATEGORY_No
#define UC_PUNCTUATION               UC_CATEGORY_P
#define UC_CONNECTOR_PUNCTUATION     UC_CATEGORY_Pc
#define UC_DASH_PUNCTUATION          UC_CATEGORY_Pd
#define UC_OPEN_PUNCTUATION          UC_CATEGORY_Ps /* a.k.a. UC_START_PUNCTUATION */
#define UC_CLOSE_PUNCTUATION         UC_CATEGORY_Pe /* a.k.a. UC_END_PUNCTUATION */
#define UC_INITIAL_QUOTE_PUNCTUATION UC_CATEGORY_Pi
#define UC_FINAL_QUOTE_PUNCTUATION   UC_CATEGORY_Pf
#define UC_OTHER_PUNCTUATION         UC_CATEGORY_Po
#define UC_SYMBOL                    UC_CATEGORY_S
#define UC_MATH_SYMBOL               UC_CATEGORY_Sm
#define UC_CURRENCY_SYMBOL           UC_CATEGORY_Sc
#define UC_MODIFIER_SYMBOL           UC_CATEGORY_Sk
#define UC_OTHER_SYMBOL              UC_CATEGORY_So
#define UC_SEPARATOR                 UC_CATEGORY_Z
#define UC_SPACE_SEPARATOR           UC_CATEGORY_Zs
#define UC_LINE_SEPARATOR            UC_CATEGORY_Zl
#define UC_PARAGRAPH_SEPARATOR       UC_CATEGORY_Zp
#define UC_OTHER                     UC_CATEGORY_C
#define UC_CONTROL                   UC_CATEGORY_Cc
#define UC_FORMAT                    UC_CATEGORY_Cf
#define UC_SURROGATE                 UC_CATEGORY_Cs /* all of them are invalid characters */
#define UC_PRIVATE_USE               UC_CATEGORY_Co
#define UC_UNASSIGNED                UC_CATEGORY_Cn /* some of them are invalid characters */

/* Return the union of two general categories.
   This corresponds to the unions of the two sets of characters.  */
extern uc_general_category_t
       uc_general_category_or (uc_general_category_t category1,
                               uc_general_category_t category2);

/* Return the intersection of two general categories as bit masks.
   This *does*not* correspond to the intersection of the two sets of
   characters.  */
extern uc_general_category_t
       uc_general_category_and (uc_general_category_t category1,
                                uc_general_category_t category2);

/* Return the intersection of a general category with the complement of a
   second general category, as bit masks.
   This *does*not* correspond to the intersection with complement, when
   viewing the categories as sets of characters.  */
extern uc_general_category_t
       uc_general_category_and_not (uc_general_category_t category1,
                                    uc_general_category_t category2);

/* Return the name of a general category.  */
extern const char *
       uc_general_category_name (uc_general_category_t category)
       _UC_ATTRIBUTE_PURE;

/* Return the long name of a general category.  */
extern const char *
       uc_general_category_long_name (uc_general_category_t category)
       _UC_ATTRIBUTE_PURE;

/* Return the general category given by name, e.g. "Lu", or by long name,
   e.g. "Uppercase Letter".  */
extern uc_general_category_t
       uc_general_category_byname (const char *category_name)
       _UC_ATTRIBUTE_PURE;

/* Return the general category of a Unicode character.  */
extern uc_general_category_t
       uc_general_category (ucs4_t uc)
       _UC_ATTRIBUTE_PURE;

/* Test whether a Unicode character belongs to a given category.
   The CATEGORY argument can be the combination of several predefined
   general categories.  */
extern bool
       uc_is_general_category (ucs4_t uc, uc_general_category_t category)
       _UC_ATTRIBUTE_PURE;
/* Likewise.  This function uses a big table comprising all categories.  */
extern bool
       uc_is_general_category_withtable (ucs4_t uc, uint32_t bitmask)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Field 3 of Unicode Character Database: Canonical combining class.  */

/* The possible results of uc_combining_class (0..255) are described in
   UCD.html.  The list here is not definitive; more values can be added
   in future versions.  */
enum
{
  UC_CCC_NR   =   0, /* Not Reordered */
  UC_CCC_OV   =   1, /* Overlay */
  UC_CCC_NK   =   7, /* Nukta */
  UC_CCC_KV   =   8, /* Kana Voicing */
  UC_CCC_VR   =   9, /* Virama */
  UC_CCC_ATBL = 200, /* Attached Below Left */
  UC_CCC_ATB  = 202, /* Attached Below */
  UC_CCC_ATA  = 214, /* Attached Above */
  UC_CCC_ATAR = 216, /* Attached Above Right */
  UC_CCC_BL   = 218, /* Below Left */
  UC_CCC_B    = 220, /* Below */
  UC_CCC_BR   = 222, /* Below Right */
  UC_CCC_L    = 224, /* Left */
  UC_CCC_R    = 226, /* Right */
  UC_CCC_AL   = 228, /* Above Left */
  UC_CCC_A    = 230, /* Above */
  UC_CCC_AR   = 232, /* Above Right */
  UC_CCC_DB   = 233, /* Double Below */
  UC_CCC_DA   = 234, /* Double Above */
  UC_CCC_IS   = 240  /* Iota Subscript */
};

/* Return the canonical combining class of a Unicode character.  */
extern int
       uc_combining_class (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Return the name of a canonical combining class.  */
extern const char *
       uc_combining_class_name (int ccc)
       _UC_ATTRIBUTE_CONST;

/* Return the long name of a canonical combining class.  */
extern const char *
       uc_combining_class_long_name (int ccc)
       _UC_ATTRIBUTE_CONST;

/* Return the canonical combining class given by name, e.g. "BL", or by long
   name, e.g. "Below Left".  */
extern int
       uc_combining_class_byname (const char *ccc_name)
       _UC_ATTRIBUTE_PURE;

/* ========================================================================= */

/* Field 4 of Unicode Character Database: Bidi class.
   Before Unicode 4.0, this field was called "Bidirectional category".  */

enum
{
  UC_BIDI_L,   /* Left-to-Right */
  UC_BIDI_LRE, /* Left-to-Right Embedding */
  UC_BIDI_LRO, /* Left-to-Right Override */
  UC_BIDI_R,   /* Right-to-Left */
  UC_BIDI_AL,  /* Right-to-Left Arabic */
  UC_BIDI_RLE, /* Right-to-Left Embedding */
  UC_BIDI_RLO, /* Right-to-Left Override */
  UC_BIDI_PDF, /* Pop Directional Format */
  UC_BIDI_EN,  /* European Number */
  UC_BIDI_ES,  /* European Number Separator */
  UC_BIDI_ET,  /* European Number Terminator */
  UC_BIDI_AN,  /* Arabic Number */
  UC_BIDI_CS,  /* Common Number Separator */
  UC_BIDI_NSM, /* Non-Spacing Mark */
  UC_BIDI_BN,  /* Boundary Neutral */
  UC_BIDI_B,   /* Paragraph Separator */
  UC_BIDI_S,   /* Segment Separator */
  UC_BIDI_WS,  /* Whitespace */
  UC_BIDI_ON,  /* Other Neutral */
  UC_BIDI_LRI, /* Left-to-Right Isolate */
  UC_BIDI_RLI, /* Right-to-Left Isolate */
  UC_BIDI_FSI, /* First Strong Isolate */
  UC_BIDI_PDI  /* Pop Directional Isolate */
};

/* Return the name of a bidi class.  */
extern const char *
       uc_bidi_class_name (int bidi_class)
       _UC_ATTRIBUTE_CONST;
/* Same; obsolete function name.  */
extern const char *
       uc_bidi_category_name (int category)
       _UC_ATTRIBUTE_CONST;

/* Return the long name of a bidi class.  */
extern const char *
       uc_bidi_class_long_name (int bidi_class)
       _UC_ATTRIBUTE_CONST;

/* Return the bidi class given by name, e.g. "LRE", or by long name, e.g.
   "Left-to-Right Embedding".  */
extern int
       uc_bidi_class_byname (const char *bidi_class_name)
       _UC_ATTRIBUTE_PURE;
/* Same; obsolete function name.  */
extern int
       uc_bidi_category_byname (const char *category_name)
       _UC_ATTRIBUTE_PURE;

/* Return the bidi class of a Unicode character.  */
extern int
       uc_bidi_class (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
/* Same; obsolete function name.  */
extern int
       uc_bidi_category (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test whether a Unicode character belongs to a given bidi class.  */
extern bool
       uc_is_bidi_class (ucs4_t uc, int bidi_class)
       _UC_ATTRIBUTE_CONST;
/* Same; obsolete function name.  */
extern bool
       uc_is_bidi_category (ucs4_t uc, int category)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Field 5 of Unicode Character Database: Character decomposition mapping.
   See "uninorm.h".  */

/* ========================================================================= */

/* Field 6 of Unicode Character Database: Decimal digit value.  */

/* Return the decimal digit value of a Unicode character.  */
extern int
       uc_decimal_value (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Field 7 of Unicode Character Database: Digit value.  */

/* Return the digit value of a Unicode character.  */
extern int
       uc_digit_value (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Field 8 of Unicode Character Database: Numeric value.  */

/* Return the numeric value of a Unicode character.  */
typedef struct
{
  int numerator;
  int denominator;
}
uc_fraction_t;
extern uc_fraction_t
       uc_numeric_value (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Field 9 of Unicode Character Database: Mirrored.  */

/* Return the mirrored character of a Unicode character UC in *PUC.  */
extern bool
       uc_mirror_char (ucs4_t uc, ucs4_t *puc);

/* ========================================================================= */

/* Field 10 of Unicode Character Database: Unicode 1.0 Name.
   Not available in this library.  */

/* ========================================================================= */

/* Field 11 of Unicode Character Database: ISO 10646 comment.
   Not available in this library.  */

/* ========================================================================= */

/* Field 12, 13, 14 of Unicode Character Database: Uppercase mapping,
   lowercase mapping, titlecase mapping.  See "unicase.h".  */

/* ========================================================================= */

/* Field 2 of the file ArabicShaping.txt in the Unicode Character Database.  */

/* Possible joining types.  */
enum
{
  UC_JOINING_TYPE_U, /* Non_Joining */
  UC_JOINING_TYPE_T, /* Transparent */
  UC_JOINING_TYPE_C, /* Join_Causing */
  UC_JOINING_TYPE_L, /* Left_Joining */
  UC_JOINING_TYPE_R, /* Right_Joining */
  UC_JOINING_TYPE_D  /* Dual_Joining */
};

/* Return the name of a joining type.  */
extern const char *
       uc_joining_type_name (int joining_type)
       _UC_ATTRIBUTE_CONST;

/* Return the long name of a joining type.  */
extern const char *
       uc_joining_type_long_name (int joining_type)
       _UC_ATTRIBUTE_CONST;

/* Return the joining type given by name, e.g. "D", or by long name, e.g.
   "Dual Joining".  */
extern int
       uc_joining_type_byname (const char *joining_type_name)
       _UC_ATTRIBUTE_PURE;

/* Return the joining type of a Unicode character.  */
extern int
       uc_joining_type (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Field 3 of the file ArabicShaping.txt in the Unicode Character Database.  */

/* Possible joining groups.
   This enumeration may be extended in the future.  */
enum
{
  UC_JOINING_GROUP_NONE,                     /* No_Joining_Group */
  UC_JOINING_GROUP_AIN,                      /* Ain */
  UC_JOINING_GROUP_ALAPH,                    /* Alaph */
  UC_JOINING_GROUP_ALEF,                     /* Alef */
  UC_JOINING_GROUP_BEH,                      /* Beh */
  UC_JOINING_GROUP_BETH,                     /* Beth */
  UC_JOINING_GROUP_BURUSHASKI_YEH_BARREE,    /* Burushaski_Yeh_Barree */
  UC_JOINING_GROUP_DAL,                      /* Dal */
  UC_JOINING_GROUP_DALATH_RISH,              /* Dalath_Rish */
  UC_JOINING_GROUP_E,                        /* E */
  UC_JOINING_GROUP_FARSI_YEH,                /* Farsi_Yeh */
  UC_JOINING_GROUP_FE,                       /* Fe */
  UC_JOINING_GROUP_FEH,                      /* Feh */
  UC_JOINING_GROUP_FINAL_SEMKATH,            /* Final_Semkath */
  UC_JOINING_GROUP_GAF,                      /* Gaf */
  UC_JOINING_GROUP_GAMAL,                    /* Gamal */
  UC_JOINING_GROUP_HAH,                      /* Hah */
  UC_JOINING_GROUP_HE,                       /* He */
  UC_JOINING_GROUP_HEH,                      /* Heh */
  UC_JOINING_GROUP_HEH_GOAL,                 /* Heh_Goal */
  UC_JOINING_GROUP_HETH,                     /* Heth */
  UC_JOINING_GROUP_KAF,                      /* Kaf */
  UC_JOINING_GROUP_KAPH,                     /* Kaph */
  UC_JOINING_GROUP_KHAPH,                    /* Khaph */
  UC_JOINING_GROUP_KNOTTED_HEH,              /* Knotted_Heh */
  UC_JOINING_GROUP_LAM,                      /* Lam */
  UC_JOINING_GROUP_LAMADH,                   /* Lamadh */
  UC_JOINING_GROUP_MEEM,                     /* Meem */
  UC_JOINING_GROUP_MIM,                      /* Mim */
  UC_JOINING_GROUP_NOON,                     /* Noon */
  UC_JOINING_GROUP_NUN,                      /* Nun */
  UC_JOINING_GROUP_NYA,                      /* Nya */
  UC_JOINING_GROUP_PE,                       /* Pe */
  UC_JOINING_GROUP_QAF,                      /* Qaf */
  UC_JOINING_GROUP_QAPH,                     /* Qaph */
  UC_JOINING_GROUP_REH,                      /* Reh */
  UC_JOINING_GROUP_REVERSED_PE,              /* Reversed_Pe */
  UC_JOINING_GROUP_SAD,                      /* Sad */
  UC_JOINING_GROUP_SADHE,                    /* Sadhe */
  UC_JOINING_GROUP_SEEN,                     /* Seen */
  UC_JOINING_GROUP_SEMKATH,                  /* Semkath */
  UC_JOINING_GROUP_SHIN,                     /* Shin */
  UC_JOINING_GROUP_SWASH_KAF,                /* Swash_Kaf */
  UC_JOINING_GROUP_SYRIAC_WAW,               /* Syriac_Waw */
  UC_JOINING_GROUP_TAH,                      /* Tah */
  UC_JOINING_GROUP_TAW,                      /* Taw */
  UC_JOINING_GROUP_TEH_MARBUTA,              /* Teh_Marbuta */
  UC_JOINING_GROUP_TEH_MARBUTA_GOAL,         /* Teh_Marbuta_Goal */
  UC_JOINING_GROUP_TETH,                     /* Teth */
  UC_JOINING_GROUP_WAW,                      /* Waw */
  UC_JOINING_GROUP_YEH,                      /* Yeh */
  UC_JOINING_GROUP_YEH_BARREE,               /* Yeh_Barree */
  UC_JOINING_GROUP_YEH_WITH_TAIL,            /* Yeh_With_Tail */
  UC_JOINING_GROUP_YUDH,                     /* Yudh */
  UC_JOINING_GROUP_YUDH_HE,                  /* Yudh_He */
  UC_JOINING_GROUP_ZAIN,                     /* Zain */
  UC_JOINING_GROUP_ZHAIN,                    /* Zhain */
  UC_JOINING_GROUP_ROHINGYA_YEH,             /* Rohingya_Yeh */
  UC_JOINING_GROUP_STRAIGHT_WAW,             /* Straight_Waw */
  UC_JOINING_GROUP_MANICHAEAN_ALEPH,         /* Manichaean_Aleph */
  UC_JOINING_GROUP_MANICHAEAN_BETH,          /* Manichaean_Beth */
  UC_JOINING_GROUP_MANICHAEAN_GIMEL,         /* Manichaean_Gimel */
  UC_JOINING_GROUP_MANICHAEAN_DALETH,        /* Manichaean_Daleth */
  UC_JOINING_GROUP_MANICHAEAN_WAW,           /* Manichaean_Waw */
  UC_JOINING_GROUP_MANICHAEAN_ZAYIN,         /* Manichaean_Zayin */
  UC_JOINING_GROUP_MANICHAEAN_HETH,          /* Manichaean_Heth */
  UC_JOINING_GROUP_MANICHAEAN_TETH,          /* Manichaean_Teth */
  UC_JOINING_GROUP_MANICHAEAN_YODH,          /* Manichaean_Yodh */
  UC_JOINING_GROUP_MANICHAEAN_KAPH,          /* Manichaean_Kaph */
  UC_JOINING_GROUP_MANICHAEAN_LAMEDH,        /* Manichaean_Lamedh */
  UC_JOINING_GROUP_MANICHAEAN_DHAMEDH,       /* Manichaean_Dhamedh */
  UC_JOINING_GROUP_MANICHAEAN_THAMEDH,       /* Manichaean_Thamedh */
  UC_JOINING_GROUP_MANICHAEAN_MEM,           /* Manichaean_Mem */
  UC_JOINING_GROUP_MANICHAEAN_NUN,           /* Manichaean_Nun */
  UC_JOINING_GROUP_MANICHAEAN_SAMEKH,        /* Manichaean_Aleph */
  UC_JOINING_GROUP_MANICHAEAN_AYIN,          /* Manichaean_Ayin */
  UC_JOINING_GROUP_MANICHAEAN_PE,            /* Manichaean_Pe */
  UC_JOINING_GROUP_MANICHAEAN_SADHE,         /* Manichaean_Sadhe */
  UC_JOINING_GROUP_MANICHAEAN_QOPH,          /* Manichaean_Qoph */
  UC_JOINING_GROUP_MANICHAEAN_RESH,          /* Manichaean_Resh */
  UC_JOINING_GROUP_MANICHAEAN_TAW,           /* Manichaean_Taw */
  UC_JOINING_GROUP_MANICHAEAN_ONE,           /* Manichaean_One */
  UC_JOINING_GROUP_MANICHAEAN_FIVE,          /* Manichaean_Five */
  UC_JOINING_GROUP_MANICHAEAN_TEN,           /* Manichaean_Ten */
  UC_JOINING_GROUP_MANICHAEAN_TWENTY,        /* Manichaean_Twenty */
  UC_JOINING_GROUP_MANICHAEAN_HUNDRED,       /* Manichaean_Hundred */
  UC_JOINING_GROUP_AFRICAN_FEH,              /* African_Feh */
  UC_JOINING_GROUP_AFRICAN_QAF,              /* African_Qaf */
  UC_JOINING_GROUP_AFRICAN_NOON,             /* African_Noon */
  UC_JOINING_GROUP_MALAYALAM_NGA,            /* Malayalam_Nga */
  UC_JOINING_GROUP_MALAYALAM_JA,             /* Malayalam_Ja */
  UC_JOINING_GROUP_MALAYALAM_NYA,            /* Malayalam_Nya */
  UC_JOINING_GROUP_MALAYALAM_TTA,            /* Malayalam_Tta */
  UC_JOINING_GROUP_MALAYALAM_NNA,            /* Malayalam_Nna */
  UC_JOINING_GROUP_MALAYALAM_NNNA,           /* Malayalam_Nnna */
  UC_JOINING_GROUP_MALAYALAM_BHA,            /* Malayalam_Bha */
  UC_JOINING_GROUP_MALAYALAM_RA,             /* Malayalam_Ra */
  UC_JOINING_GROUP_MALAYALAM_LLA,            /* Malayalam_Lla */
  UC_JOINING_GROUP_MALAYALAM_LLLA,           /* Malayalam_Llla */
  UC_JOINING_GROUP_MALAYALAM_SSA,            /* Malayalam_Ssa */
  UC_JOINING_GROUP_HANIFI_ROHINGYA_PA,       /* Hanifi_Rohingya_Pa */
  UC_JOINING_GROUP_HANIFI_ROHINGYA_KINNA_YA, /* Hanifi_Rohingya_Kinna_Ya */
  UC_JOINING_GROUP_THIN_YEH,                 /* Thin_Yeh */
  UC_JOINING_GROUP_VERTICAL_TAIL             /* Vertical_Tail */
};

/* Return the name of a joining group.  */
extern const char *
       uc_joining_group_name (int joining_group)
       _UC_ATTRIBUTE_CONST;

/* Return the joining group given by name, e.g. "Teh_Marbuta".  */
extern int
       uc_joining_group_byname (const char *joining_group_name)
       _UC_ATTRIBUTE_PURE;

/* Return the joining group of a Unicode character.  */
extern int
       uc_joining_group (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Common API for properties.  */

/* Data type denoting a property.  This is not just a number, but rather a
   pointer to the test functions, so that programs that use only few of the
   properties don't have a link-time dependency towards all the tables.  */
typedef struct
{
  bool (*test_fn) (ucs4_t uc);
}
uc_property_t;

/* Predefined properties.  */
/* General.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_WHITE_SPACE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_ALPHABETIC;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_ALPHABETIC;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_NOT_A_CHARACTER;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_DEFAULT_IGNORABLE_CODE_POINT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_DEFAULT_IGNORABLE_CODE_POINT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_DEPRECATED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_LOGICAL_ORDER_EXCEPTION;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_VARIATION_SELECTOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_PRIVATE_USE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_UNASSIGNED_CODE_VALUE;
/* Case.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_UPPERCASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_UPPERCASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_LOWERCASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_LOWERCASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_TITLECASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CASED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CASE_IGNORABLE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CHANGES_WHEN_LOWERCASED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CHANGES_WHEN_UPPERCASED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CHANGES_WHEN_TITLECASED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CHANGES_WHEN_CASEFOLDED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CHANGES_WHEN_CASEMAPPED;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_SOFT_DOTTED;
/* Identifiers.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_ID_START;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_ID_START;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_ID_CONTINUE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_ID_CONTINUE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_XID_START;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_XID_CONTINUE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_PATTERN_WHITE_SPACE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_PATTERN_SYNTAX;
/* Shaping and rendering.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_JOIN_CONTROL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_GRAPHEME_BASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_GRAPHEME_EXTEND;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_GRAPHEME_EXTEND;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_GRAPHEME_LINK;
/* Bidi.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_CONTROL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_LEFT_TO_RIGHT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_HEBREW_RIGHT_TO_LEFT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_ARABIC_RIGHT_TO_LEFT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_EUROPEAN_DIGIT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_EUR_NUM_SEPARATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_EUR_NUM_TERMINATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_ARABIC_DIGIT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_COMMON_SEPARATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_BLOCK_SEPARATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_SEGMENT_SEPARATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_WHITESPACE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_NON_SPACING_MARK;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_BOUNDARY_NEUTRAL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_PDF;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_EMBEDDING_OR_OVERRIDE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_BIDI_OTHER_NEUTRAL;
/* Numeric.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_HEX_DIGIT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_ASCII_HEX_DIGIT;
/* CJK.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_IDEOGRAPHIC;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_UNIFIED_IDEOGRAPH;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_RADICAL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_IDS_BINARY_OPERATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_IDS_TRINARY_OPERATOR;
/* Emoji.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EMOJI;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EMOJI_PRESENTATION;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EMOJI_MODIFIER;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EMOJI_MODIFIER_BASE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EMOJI_COMPONENT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EXTENDED_PICTOGRAPHIC;
/* Misc.  */
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_ZERO_WIDTH;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_SPACE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_NON_BREAK;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_ISO_CONTROL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_FORMAT_CONTROL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_DASH;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_HYPHEN;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_PUNCTUATION;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_LINE_SEPARATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_PARAGRAPH_SEPARATOR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_QUOTATION_MARK;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_SENTENCE_TERMINAL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_TERMINAL_PUNCTUATION;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_CURRENCY_SYMBOL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_MATH;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_OTHER_MATH;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_PAIRED_PUNCTUATION;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_LEFT_OF_PAIR;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_COMBINING;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_COMPOSITE;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_DECIMAL_DIGIT;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_NUMERIC;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_DIACRITIC;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_EXTENDER;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_IGNORABLE_CONTROL;
extern LIBUNISTRING_DLL_VARIABLE const uc_property_t UC_PROPERTY_REGIONAL_INDICATOR;

/* Return the property given by name, e.g. "White space".  */
extern uc_property_t
       uc_property_byname (const char *property_name);

/* Test whether a property is valid.  */
#define uc_property_is_valid(property) ((property).test_fn != NULL)

/* Test whether a Unicode character has a given property.  */
extern bool
       uc_is_property (ucs4_t uc, uc_property_t property);
extern bool uc_is_property_white_space (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_alphabetic (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_alphabetic (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_not_a_character (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_default_ignorable_code_point (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_default_ignorable_code_point (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_deprecated (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_logical_order_exception (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_variation_selector (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_private_use (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_unassigned_code_value (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_uppercase (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_uppercase (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_lowercase (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_lowercase (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_titlecase (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_cased (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_case_ignorable (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_changes_when_lowercased (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_changes_when_uppercased (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_changes_when_titlecased (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_changes_when_casefolded (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_changes_when_casemapped (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_soft_dotted (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_id_start (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_id_start (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_id_continue (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_id_continue (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_xid_start (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_xid_continue (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_pattern_white_space (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_pattern_syntax (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_join_control (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_grapheme_base (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_grapheme_extend (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_grapheme_extend (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_grapheme_link (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_control (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_left_to_right (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_hebrew_right_to_left (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_arabic_right_to_left (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_european_digit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_eur_num_separator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_eur_num_terminator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_arabic_digit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_common_separator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_block_separator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_segment_separator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_whitespace (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_non_spacing_mark (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_boundary_neutral (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_pdf (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_embedding_or_override (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_bidi_other_neutral (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_hex_digit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_ascii_hex_digit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_ideographic (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_unified_ideograph (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_radical (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_ids_binary_operator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_ids_trinary_operator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_emoji (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_emoji_presentation (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_emoji_modifier (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_emoji_modifier_base (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_emoji_component (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_extended_pictographic (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_zero_width (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_space (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_non_break (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_iso_control (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_format_control (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_dash (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_hyphen (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_punctuation (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_line_separator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_paragraph_separator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_quotation_mark (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_sentence_terminal (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_terminal_punctuation (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_currency_symbol (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_math (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_other_math (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_paired_punctuation (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_left_of_pair (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_combining (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_composite (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_decimal_digit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_numeric (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_diacritic (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_extender (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_ignorable_control (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;
extern bool uc_is_property_regional_indicator (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Subdivision of the Unicode characters into scripts.  */

typedef struct
{
  unsigned int code : 21;
  unsigned int start : 1;
  unsigned int end : 1;
}
uc_interval_t;
typedef struct
{
  unsigned int nintervals;
  const uc_interval_t *intervals;
  const char *name;
}
uc_script_t;

/* Return the script of a Unicode character.  */
extern const uc_script_t *
       uc_script (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Return the script given by name, e.g. "HAN".  */
extern const uc_script_t *
       uc_script_byname (const char *script_name)
       _UC_ATTRIBUTE_PURE;

/* Test whether a Unicode character belongs to a given script.  */
extern bool
       uc_is_script (ucs4_t uc, const uc_script_t *script)
       _UC_ATTRIBUTE_PURE;

/* Get the list of all scripts.  */
extern void
       uc_all_scripts (const uc_script_t **scripts, size_t *count);

/* ========================================================================= */

/* Subdivision of the Unicode character range into blocks.  */

typedef struct
{
  ucs4_t start;
  ucs4_t end;
  const char *name;
}
uc_block_t;

/* Return the block a character belongs to.  */
extern const uc_block_t *
       uc_block (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test whether a Unicode character belongs to a given block.  */
extern bool
       uc_is_block (ucs4_t uc, const uc_block_t *block)
       _UC_ATTRIBUTE_PURE;

/* Get the list of all blocks.  */
extern void
       uc_all_blocks (const uc_block_t **blocks, size_t *count);

/* ========================================================================= */

/* Properties taken from language standards.  */

/* Test whether a Unicode character is considered whitespace in ISO C 99.  */
extern bool
       uc_is_c_whitespace (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test whether a Unicode character is considered whitespace in Java.  */
extern bool
       uc_is_java_whitespace (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

enum
{
  UC_IDENTIFIER_START,    /* valid as first or subsequent character */
  UC_IDENTIFIER_VALID,    /* valid as subsequent character only */
  UC_IDENTIFIER_INVALID,  /* not valid */
  UC_IDENTIFIER_IGNORABLE /* ignorable (Java only) */
};

/* Return the categorization of a Unicode character w.r.t. the ISO C 99
   identifier syntax.  */
extern int
       uc_c_ident_category (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Return the categorization of a Unicode character w.r.t. the Java
   identifier syntax.  */
extern int
       uc_java_ident_category (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

/* Like ISO C <ctype.h> and <wctype.h>.  These functions are deprecated,
   because this set of functions was designed with ASCII in mind and cannot
   reflect the more diverse reality of the Unicode character set.  But they
   can be a quick-and-dirty porting aid when migrating from wchar_t APIs
   to Unicode strings.  */

/* Test for any character for which 'uc_is_alpha' or 'uc_is_digit' is true.  */
extern bool
       uc_is_alnum (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character for which 'uc_is_upper' or 'uc_is_lower' is true,
   or any character that is one of a locale-specific set of characters for
   which none of 'uc_is_cntrl', 'uc_is_digit', 'uc_is_punct', or 'uc_is_space'
   is true.  */
extern bool
       uc_is_alpha (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any control character.  */
extern bool
       uc_is_cntrl (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character that corresponds to a decimal-digit character.  */
extern bool
       uc_is_digit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character for which 'uc_is_print' is true and 'uc_is_space'
   is false.  */
extern bool
       uc_is_graph (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character that corresponds to a lowercase letter or is one
   of a locale-specific set of characters for which none of 'uc_is_cntrl',
   'uc_is_digit', 'uc_is_punct', or 'uc_is_space' is true.  */
extern bool
       uc_is_lower (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any printing character.  */
extern bool
       uc_is_print (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any printing character that is one of a locale-specific set of
   characters for which neither 'uc_is_space' nor 'uc_is_alnum' is true.  */
extern bool
       uc_is_punct (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character that corresponds to a locale-specific set of
   characters for which none of 'uc_is_alnum', 'uc_is_graph', or 'uc_is_punct'
   is true.  */
extern bool
       uc_is_space (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character that corresponds to an uppercase letter or is one
   of a locale-specific set of character for which none of 'uc_is_cntrl',
   'uc_is_digit', 'uc_is_punct', or 'uc_is_space' is true.  */
extern bool
       uc_is_upper (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* Test for any character that corresponds to a hexadecimal-digit
   character.  */
extern bool
       uc_is_xdigit (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* GNU extension. */
/* Test for any character that corresponds to a standard blank character or
   a locale-specific set of characters for which 'uc_is_alnum' is false.  */
extern bool
       uc_is_blank (ucs4_t uc)
       _UC_ATTRIBUTE_CONST;

/* ========================================================================= */

#ifdef __cplusplus
}
#endif

#endif /* _UNICTYPE_H */
