/*
 * Copyright(c) 2014-2024 Tim Ruehsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * This file is part of libpsl.
 *
 * Header file for libpsl library routines
 *
 * Changelog
 * 20.03.2014  Tim Ruehsen  created
 *
 */

#ifndef LIBPSL_LIBPSL_H
#define LIBPSL_LIBPSL_H

#include <stdio.h>
#include <time.h>

#define PSL_VERSION "0.21.5"
#define PSL_VERSION_MAJOR 0
#define PSL_VERSION_MINOR 21
#define PSL_VERSION_PATCH 5
#define PSL_VERSION_NUMBER 0x001505

/* support clang's __has_declspec_attribute attribute */
#ifndef __has_declspec_attribute
#  define __has_declspec_attribute(x) 0
#endif

#ifndef PSL_API
#if defined BUILDING_PSL && HAVE_VISIBILITY
#  define PSL_API __attribute__ ((__visibility__("default")))
#elif defined BUILDING_PSL && (defined _MSC_VER || __has_declspec_attribute(dllexport)) && !defined PSL_STATIC
#  define PSL_API __declspec(dllexport)
#elif (defined _MSC_VER || __has_declspec_attribute(dllimport)) && !defined PSL_STATIC
#  define PSL_API __declspec(dllimport)
#else
#  define PSL_API
#endif
#endif

#ifdef  __cplusplus
extern "C" {
#endif

/* types for psl_is_public_suffix2() */
#define PSL_TYPE_ICANN        (1<<0)
#define PSL_TYPE_PRIVATE      (1<<1)
#define PSL_TYPE_NO_STAR_RULE (1<<2)
#define PSL_TYPE_ANY          (PSL_TYPE_ICANN | PSL_TYPE_PRIVATE)

/**
 * psl_error_t:
 * @PSL_SUCCESS: Successful return.
 * @PSL_ERR_INVALID_ARG: Invalid argument.
 * @PSL_ERR_CONVERTER: Failed to open libicu utf-16 converter.
 * @PSL_ERR_TO_UTF16: Failed to convert to utf-16.
 * @PSL_ERR_TO_LOWER: Failed to convert utf-16 to lowercase.
 * @PSL_ERR_TO_UTF8: Failed to convert utf-16 to utf-8.
 * @PSL_ERR_NO_MEM: Failed to allocate memory.
 *
 * Return codes for PSL functions.
 * Negative return codes mean failure.
 * Positive values are reserved for non-error return codes.
 */
typedef enum {
	PSL_SUCCESS = 0,
	PSL_ERR_INVALID_ARG = -1,
	PSL_ERR_CONVERTER = -2, /* failed to open libicu utf-16 converter */
	PSL_ERR_TO_UTF16 = -3,  /* failed to convert to utf-16 */
	PSL_ERR_TO_LOWER = -4,  /* failed to convert utf-16 to lowercase */
	PSL_ERR_TO_UTF8 = -5,   /* failed to convert utf-16 to utf-8 */
	PSL_ERR_NO_MEM = -6    /* failed to allocate memory */
} psl_error_t;

typedef struct psl_ctx_st psl_ctx_t;

/* frees PSL context */
PSL_API
void
	psl_free(psl_ctx_t *psl);

/* frees memory allocated by libpsl routines */
PSL_API
void
	psl_free_string(char *str);

/* loads PSL data from file */
PSL_API
psl_ctx_t *
	psl_load_file(const char *fname);

/* loads PSL data from FILE pointer */
PSL_API
psl_ctx_t *
	psl_load_fp(FILE *fp);

/* retrieves builtin PSL data */
PSL_API
const psl_ctx_t *
	psl_builtin(void);

/* retrieves most recent PSL data */
PSL_API
psl_ctx_t *
	psl_latest(const char *fname);

/* checks whether domain is a public suffix or not */
PSL_API
int
	psl_is_public_suffix(const psl_ctx_t *psl, const char *domain);

/* checks whether domain is a public suffix regarding the type or not */
PSL_API
int
	psl_is_public_suffix2(const psl_ctx_t *psl, const char *domain, int type);

/* checks whether cookie_domain is acceptable for domain or not */
PSL_API
int
	psl_is_cookie_domain_acceptable(const psl_ctx_t *psl, const char *hostname, const char *cookie_domain);

/* returns the longest not registrable domain within 'domain' or NULL if none found */
PSL_API
const char *
	psl_unregistrable_domain(const psl_ctx_t *psl, const char *domain);

/* returns the shortest possible registrable domain part or NULL if domain is not registrable at all */
PSL_API
const char *
	psl_registrable_domain(const psl_ctx_t *psl, const char *domain);

/* convert a string into lowercase UTF-8 */
PSL_API
psl_error_t
	psl_str_to_utf8lower(const char *str, const char *encoding, const char *locale, char **lower);

/* does not include exceptions */
PSL_API
int
	psl_suffix_count(const psl_ctx_t *psl);

/* just counts exceptions */
PSL_API
int
	psl_suffix_exception_count(const psl_ctx_t *psl);

/* just counts wildcards */
PSL_API
int
	psl_suffix_wildcard_count(const psl_ctx_t *psl);

/* returns mtime of PSL source file */
PSL_API
time_t
	psl_builtin_file_time(void);

/* returns SHA1 checksum (hex-encoded, lowercase) of PSL source file */
PSL_API
const char *
	psl_builtin_sha1sum(void);

/* returns file name of PSL source file */
PSL_API
const char *
	psl_builtin_filename(void);

/* returns name of distribution PSL data file */
PSL_API
const char *
	psl_dist_filename(void);

/* returns library version string */
PSL_API
const char *
	psl_get_version(void);

/* checks library version number */
PSL_API
int
	psl_check_version_number(int version);

/* returns whether the built-in data is outdated or not */
PSL_API
int
	psl_builtin_outdated(void);

#ifdef  __cplusplus
}
#endif

#endif /* LIBPSL_LIBPSL_H */
