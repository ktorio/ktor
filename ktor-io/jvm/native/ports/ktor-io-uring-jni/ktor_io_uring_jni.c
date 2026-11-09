/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/eventfd.h>
#include <unistd.h>

#include <liburing.h>

struct ktor_io_uring_ring {
    struct io_uring ring;
    char **open_paths;
    unsigned entries;
    int event_fd;
};

static struct ktor_io_uring_ring *get_ring(jlong value) {
    return (struct ktor_io_uring_ring *) (intptr_t) value;
}

static int submit_ring(struct ktor_io_uring_ring *handle) {
    int result = io_uring_submit(&handle->ring);
    return result < 0 ? result : 0;
}

static char *to_utf8(JNIEnv *env, jstring value) {
    if (value == NULL) return NULL;

    jsize length = (*env)->GetStringLength(env, value);
    const jchar *characters = (*env)->GetStringChars(env, value, NULL);
    if (characters == NULL) return NULL;

    char *result = malloc((size_t) length * 4 + 1);
    if (result == NULL) {
        (*env)->ReleaseStringChars(env, value, characters);
        return NULL;
    }

    size_t output = 0;
    for (jsize index = 0; index < length; index++) {
        uint32_t character = characters[index];
        if (character >= 0xd800 && character <= 0xdbff && index + 1 < length) {
            uint32_t next = characters[index + 1];
            if (next >= 0xdc00 && next <= 0xdfff) {
                character = 0x10000 + ((character - 0xd800) << 10) + (next - 0xdc00);
                index++;
            } else {
                character = 0xfffd;
            }
        } else if (character >= 0xdc00 && character <= 0xdfff) {
            character = 0xfffd;
        }

        if (character <= 0x7f) {
            result[output++] = (char) character;
        } else if (character <= 0x7ff) {
            result[output++] = (char) (0xc0 | (character >> 6));
            result[output++] = (char) (0x80 | (character & 0x3f));
        } else if (character <= 0xffff) {
            result[output++] = (char) (0xe0 | (character >> 12));
            result[output++] = (char) (0x80 | ((character >> 6) & 0x3f));
            result[output++] = (char) (0x80 | (character & 0x3f));
        } else {
            result[output++] = (char) (0xf0 | (character >> 18));
            result[output++] = (char) (0x80 | ((character >> 12) & 0x3f));
            result[output++] = (char) (0x80 | ((character >> 6) & 0x3f));
            result[output++] = (char) (0x80 | (character & 0x3f));
        }
    }
    result[output] = '\0';
    (*env)->ReleaseStringChars(env, value, characters);
    return result;
}

JNIEXPORT jlong JNICALL Java_io_ktor_utils_io_files_IoUringNative_ringCreate(
    JNIEnv *env,
    jobject receiver,
    jint entries
) {
    if (entries <= 0) return 0;

    struct ktor_io_uring_ring *handle = calloc(1, sizeof(*handle));
    if (handle == NULL) return 0;
    handle->open_paths = calloc((size_t) entries, sizeof(*handle->open_paths));
    if (handle->open_paths == NULL) {
        free(handle);
        return 0;
    }
    handle->event_fd = -1;

    int result = io_uring_queue_init((unsigned) entries, &handle->ring, 0);
    if (result < 0) {
        free(handle->open_paths);
        free(handle);
        return 0;
    }

    handle->entries = (unsigned) entries;
    return (jlong) (intptr_t) handle;
}

JNIEXPORT void JNICALL Java_io_ktor_utils_io_files_IoUringNative_ringDestroy(
    JNIEnv *env,
    jobject receiver,
    jlong value
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL) return;

    if (handle->event_fd >= 0) {
        io_uring_unregister_eventfd(&handle->ring);
        close(handle->event_fd);
    }
    io_uring_queue_exit(&handle->ring);
    for (unsigned index = 0; index < handle->entries; index++) free(handle->open_paths[index]);
    free(handle->open_paths);
    free(handle);
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_submitOpen(
    JNIEnv *env,
    jobject receiver,
    jlong value,
    jstring path,
    jint slot
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL || slot < 0 || (unsigned) slot >= handle->entries) return -EINVAL;

    char *path_copy = to_utf8(env, path);
    if (path_copy == NULL) return -ENOMEM;

    struct io_uring_sqe *sqe = io_uring_get_sqe(&handle->ring);
    if (sqe == NULL) {
        free(path_copy);
        return -EAGAIN;
    }

    free(handle->open_paths[slot]);
    handle->open_paths[slot] = path_copy;
    io_uring_prep_openat(sqe, AT_FDCWD, path_copy, O_RDONLY | O_CLOEXEC, 0);
    io_uring_sqe_set_data64(sqe, (uint64_t) slot);
    int result = submit_ring(handle);
    if (result < 0) {
        handle->open_paths[slot] = NULL;
        free(path_copy);
    }
    return result;
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_submitRead(
    JNIEnv *env,
    jobject receiver,
    jlong value,
    jint fd,
    jlong address,
    jint length,
    jlong offset,
    jint slot
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL) return -EINVAL;

    struct io_uring_sqe *sqe = io_uring_get_sqe(&handle->ring);
    if (sqe == NULL) return -EAGAIN;

    io_uring_prep_read(sqe, fd, (void *) (intptr_t) address, (unsigned) length, (uint64_t) offset);
    io_uring_sqe_set_data64(sqe, (uint64_t) slot);
    return submit_ring(handle);
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_submitClose(
    JNIEnv *env,
    jobject receiver,
    jlong value,
    jint fd,
    jint slot
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL) return -EINVAL;

    struct io_uring_sqe *sqe = io_uring_get_sqe(&handle->ring);
    if (sqe == NULL) return -EAGAIN;

    io_uring_prep_close(sqe, fd);
    io_uring_sqe_set_data64(sqe, (uint64_t) slot);
    return submit_ring(handle);
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_submitCancel(
    JNIEnv *env,
    jobject receiver,
    jlong value,
    jint slot
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL) return -EINVAL;

    struct io_uring_sqe *sqe = io_uring_get_sqe(&handle->ring);
    if (sqe == NULL) return -EAGAIN;

    io_uring_prep_cancel64(sqe, (uint64_t) slot, 0);
    io_uring_sqe_set_data64(sqe, UINT64_MAX);
    return submit_ring(handle);
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_submitWake(
    JNIEnv *env,
    jobject receiver,
    jlong value
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL) return -EINVAL;

    struct io_uring_sqe *sqe = io_uring_get_sqe(&handle->ring);
    if (sqe == NULL) return -EAGAIN;

    io_uring_prep_nop(sqe);
    io_uring_sqe_set_data64(sqe, UINT64_MAX - 1);
    return submit_ring(handle);
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_waitCompletions(
    JNIEnv *env,
    jobject receiver,
    jlong value,
    jintArray slots,
    jintArray results
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL || slots == NULL || results == NULL) return -EINVAL;

    struct io_uring_cqe *first = NULL;
    int result = io_uring_wait_cqe(&handle->ring, &first);
    if (result < 0) return result;

    jsize slots_length = (*env)->GetArrayLength(env, slots);
    jsize results_length = (*env)->GetArrayLength(env, results);
    unsigned capacity = (unsigned) slots_length;
    if (capacity > (unsigned) results_length) capacity = (unsigned) results_length;
    if (capacity > handle->entries) capacity = handle->entries;
    if (capacity == 0) return -EINVAL;

    struct io_uring_cqe *cqes[capacity];
    unsigned count = io_uring_peek_batch_cqe(&handle->ring, cqes, capacity);
    if (count == 0) {
        cqes[0] = first;
        count = 1;
    }

    jint slot_values[count];
    jint result_values[count];
    for (unsigned index = 0; index < count; index++) {
        uint64_t user_data = io_uring_cqe_get_data64(cqes[index]);
        slot_values[index] = (jint) (int32_t) (uint32_t) user_data;
        result_values[index] = cqes[index]->res;
        if (user_data < handle->entries) {
            free(handle->open_paths[user_data]);
            handle->open_paths[user_data] = NULL;
        }
    }
    io_uring_cq_advance(&handle->ring, count);

    (*env)->SetIntArrayRegion(env, slots, 0, (jsize) count, slot_values);
    (*env)->SetIntArrayRegion(env, results, 0, (jsize) count, result_values);
    return (*env)->ExceptionCheck(env) ? -EFAULT : (jint) count;
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_registerEventFd(
    JNIEnv *env,
    jobject receiver,
    jlong value
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL) return -EINVAL;
    if (handle->event_fd >= 0) return -EALREADY;

    int event_fd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
    if (event_fd < 0) return -errno;

    int result = io_uring_register_eventfd(&handle->ring, event_fd);
    if (result < 0) {
        close(event_fd);
        return result;
    }
    handle->event_fd = event_fd;
    return event_fd;
}

JNIEXPORT jint JNICALL Java_io_ktor_utils_io_files_IoUringNative_drainCompletions(
    JNIEnv *env,
    jobject receiver,
    jlong value,
    jintArray slots,
    jintArray results
) {
    struct ktor_io_uring_ring *handle = get_ring(value);
    if (handle == NULL || slots == NULL || results == NULL) return -EINVAL;
    if (handle->event_fd < 0) return -ENOSYS;

    uint64_t event_count;
    while (read(handle->event_fd, &event_count, sizeof(event_count)) < 0) {
        if (errno == EINTR) continue;
        if (errno == EAGAIN || errno == EWOULDBLOCK) break;
        return -errno;
    }

    jsize slots_length = (*env)->GetArrayLength(env, slots);
    jsize results_length = (*env)->GetArrayLength(env, results);
    unsigned capacity = (unsigned) slots_length;
    if (capacity > (unsigned) results_length) capacity = (unsigned) results_length;
    if (capacity > handle->entries) capacity = handle->entries;
    if (capacity == 0) return -EINVAL;

    struct io_uring_cqe *cqes[capacity];
    unsigned count = io_uring_peek_batch_cqe(&handle->ring, cqes, capacity);
    if (count == 0) return 0;

    jint slot_values[count];
    jint result_values[count];
    for (unsigned index = 0; index < count; index++) {
        uint64_t user_data = io_uring_cqe_get_data64(cqes[index]);
        slot_values[index] = (jint) (int32_t) (uint32_t) user_data;
        result_values[index] = cqes[index]->res;
        if (user_data < handle->entries) {
            free(handle->open_paths[user_data]);
            handle->open_paths[user_data] = NULL;
        }
    }
    io_uring_cq_advance(&handle->ring, count);

    (*env)->SetIntArrayRegion(env, slots, 0, (jsize) count, slot_values);
    (*env)->SetIntArrayRegion(env, results, 0, (jsize) count, result_values);
    return (*env)->ExceptionCheck(env) ? -EFAULT : (jint) count;
}

JNIEXPORT jobject JNICALL Java_io_ktor_utils_io_files_IoUringNative_allocateBuffer(
    JNIEnv *env,
    jobject receiver,
    jint size
) {
    if (size <= 0) return NULL;
    void *memory = malloc((size_t) size);
    if (memory == NULL) return NULL;

    jobject buffer = (*env)->NewDirectByteBuffer(env, memory, size);
    if (buffer == NULL) free(memory);
    return buffer;
}

JNIEXPORT jlong JNICALL Java_io_ktor_utils_io_files_IoUringNative_bufferAddress(
    JNIEnv *env,
    jobject receiver,
    jobject buffer
) {
    return (jlong) (intptr_t) (*env)->GetDirectBufferAddress(env, buffer);
}

JNIEXPORT void JNICALL Java_io_ktor_utils_io_files_IoUringNative_freeBuffer(
    JNIEnv *env,
    jobject receiver,
    jobject buffer
) {
    void *address = (*env)->GetDirectBufferAddress(env, buffer);
    free(address);
}