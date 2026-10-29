#!/usr/bin/env python3
# Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.

"""Close Ktor GitHub issues when their YouTrack counterparts are resolved."""

import email.utils
import http.client
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

HOST = 'https://youtrack.jetbrains.com'
APP = 'ktor-github-issues'
REQUEST_TIMEOUT = 60
READ_ATTEMPTS = 3
MAX_RETRY_DELAY = 60
RETRY_STATUSES = {408, 429, 500, 502, 503, 504}


class ApiError(RuntimeError):
    def __init__(self, status, path):
        super().__init__(f'HTTP {status} for {path}')
        self.status = status


class Client:
    def __init__(self):
        self.youtrack_token = os.environ.get('YOUTRACK_TOKEN')
        self.github_token = os.environ.get('GH_TOKEN')
        if not self.youtrack_token or not self.github_token:
            raise SystemExit('YOUTRACK_TOKEN and GH_TOKEN are required.')
        self.last_write = 0

    def request(self, host, token, method, path, body=None):
        if host == 'https://api.github.com' and method != 'GET':
            time.sleep(max(0, 2 - (time.monotonic() - self.last_write)))
            self.last_write = time.monotonic()
        message = urllib.request.Request(
            host + path,
            data=json.dumps(body).encode() if body is not None else None,
            method=method,
            headers={
                'Authorization': 'Bearer ' + token,
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'X-GitHub-Api-Version': '2022-11-28',
            },
        )
        attempts = READ_ATTEMPTS if method == 'GET' else 1
        for attempt in range(1, attempts + 1):
            started = time.monotonic()
            label = f'{method} {host}{path}'
            print(f'{label}: attempt {attempt}/{attempts}, timeout {REQUEST_TIMEOUT}s.', flush=True)
            delay = 2**attempt
            try:
                with urllib.request.urlopen(message, timeout=REQUEST_TIMEOUT) as response:
                    raw = response.read()
                    result = json.loads(raw) if raw else None
                print(f'{label}: completed in {time.monotonic() - started:.1f}s.', flush=True)
                return result
            except urllib.error.HTTPError as error:
                failure = ApiError(error.code, f'{method} {host}{path}')
                retryable = error.code in RETRY_STATUSES
                retry_after = error.headers.get('Retry-After')
                if retry_after:
                    try:
                        delay = max(delay, int(retry_after))
                    except ValueError:
                        try:
                            retry_at = email.utils.parsedate_to_datetime(retry_after).timestamp()
                            delay = max(delay, retry_at - time.time())
                        except (ValueError, TypeError, OverflowError):
                            pass
                    retryable = retryable or error.code == 403
                error.close()
            except (OSError, urllib.error.URLError, http.client.HTTPException) as error:
                failure = RuntimeError(f'{label}: {type(error).__name__} ({error})')
                retryable = True
            except json.JSONDecodeError:
                raise RuntimeError(f'{label}: invalid JSON response.') from None
            elapsed = time.monotonic() - started
            if not retryable or attempt == attempts or delay > MAX_RETRY_DELAY:
                print(f'{failure}. Failed after {elapsed:.1f}s on attempt {attempt}/{attempts}.', flush=True)
                if delay > MAX_RETRY_DELAY:
                    print(
                        f'{label}: retry delay {delay:.1f}s exceeds {MAX_RETRY_DELAY}s. Defer to the next job.',
                        flush=True,
                    )
                raise failure from None
            print(f'{failure}. Failed after {elapsed:.1f}s. Retry in {delay:.1f}s.', flush=True)
            time.sleep(delay)

    def github(self, method, path, body=None):
        return self.request('https://api.github.com', self.github_token, method, path, body)

    def youtrack(self, method, path, body=None):
        return self.request(HOST, self.youtrack_token, method, path, body)


def synchronize(client, issue):
    identifier = issue.get('id', '')
    match = re.fullmatch(r'https://github.com/ktorio/ktor/issues/([1-9][0-9]*)', issue.get('githubIssueUrl') or '')
    if (
        not re.fullmatch(r'KTOR-[1-9][0-9]*', identifier)
        or not match
        or not issue.get('resolved')
        or issue.get('githubClosureSynced')
    ):
        return 'ignored'
    resolved_at = issue.get('resolvedAt')
    if not isinstance(resolved_at, int) or resolved_at < 1 or not issue.get('stateName'):
        raise RuntimeError(f'Missing resolution metadata for {identifier}')
    path = '/repos/ktorio/ktor/issues/' + match[1]
    github_issue = client.github('GET', path)
    if github_issue.get('pull_request') is not None:
        return 'pull request'
    marker = f'<!-- ktor-youtrack-closed:{identifier}:{resolved_at} -->'
    if github_issue['state'] != 'closed' or issue.get('githubClosureRequested'):
        notified = False
        for page in range(1, 1000):
            comments = client.github('GET', path + f'/comments?per_page=100&page={page}')
            notified = any(marker in (comment.get('body') or '') for comment in comments)
            if notified or len(comments) < 100:
                break
        else:
            raise RuntimeError(f'Comment pagination did not finish for {identifier}')
        if not notified:
            body = f'The YouTrack ticket [{identifier}]({HOST}/issue/{identifier}) was closed as **{issue["stateName"]}**.\n\n{marker}'
            client.github('POST', path + '/comments', {'body': body})
        if github_issue['state'] != 'closed':
            result = client.github('PATCH', path, {'state': 'closed'})
            if result['state'] != 'closed':
                raise RuntimeError(f'The GitHub issue remains open for {identifier}')
    client.youtrack(
        'POST',
        f'/api/issues/{identifier}/extensionEndpoints/{APP}/github-metadata/acknowledge',
        {'githubIssueUrl': issue['githubIssueUrl'], 'resolvedAt': resolved_at},
    )
    return 'synchronized'


def main():
    client = Client()
    try:
        issues = client.youtrack('GET', f'/api/admin/projects/KTOR/extensionEndpoints/{APP}/github-metadata/pending')[
            'issues'
        ]
    except RuntimeError as error:
        print(f"::error::Can't get pending YouTrack tickets: {error}", file=sys.stderr, flush=True)
        raise SystemExit(1) from None
    failures = 0
    synchronized = 0
    for issue in issues:
        try:
            outcome = synchronize(client, issue)
            synchronized += outcome == 'synchronized'
            print(f'{issue["id"]}: {outcome}', flush=True)
        except (ApiError, RuntimeError) as error:
            if isinstance(error, ApiError) and error.status == 409:
                print(
                    f'{issue["id"]}: YouTrack changed during synchronization. The next job checks it again.', flush=True
                )
                continue
            failures += 1
            print(f'{issue["id"]}: synchronization failed ({error}).', flush=True)
    print(f'Checked {len(issues)} pending tickets. Synchronized {synchronized}. Failed {failures}.')
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
