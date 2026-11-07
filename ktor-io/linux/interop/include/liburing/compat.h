/* SPDX-License-Identifier: MIT */
#ifndef LIBURING_COMPAT_H
#define LIBURING_COMPAT_H

typedef int __kernel_rwf_t;

#if defined(__has_include)
/* introduced in C++17 & C23 */
/* quotes "" quotes needed for GCC < 10 */
#if __has_include("linux/time_types.h")
#include <linux/time_types.h>
#else
struct __kernel_timespec {
	int64_t		tv_sec;
	long long	tv_nsec;
};
#endif

#define UAPI_LINUX_IO_URING_H_SKIP_LINUX_TIME_TYPES_H 1
#endif

#if !defined(__has_include)
#include <stdint.h>

struct __kernel_timespec {
	int64_t		tv_sec;
	long long	tv_nsec;
};

/* <linux/time_types.h> is not available, so it can't be included */
#define UAPI_LINUX_IO_URING_H_SKIP_LINUX_TIME_TYPES_H 1
#endif
#include <inttypes.h>

struct open_how {
	uint64_t	flags;
	uint64_t	mode;
	uint64_t	resolve;
};

#include <inttypes.h>

#define FUTEX_32	2
#define FUTEX_WAITV_MAX	128

struct futex_waitv {
	uint64_t	val;
	uint64_t	uaddr;
	uint32_t	flags;
	uint32_t	__reserved;
};


#include <linux/ioctl.h>

#ifndef BLOCK_URING_CMD_DISCARD
#define BLOCK_URING_CMD_DISCARD                        _IO(0x12, 0)
#endif

#endif
