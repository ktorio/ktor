# How to contribute

Before contributing to ktor, it could be advisable 
to check [Slack](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) **#ktor** channel first 
and discuss the problem with the communitity.

If your contribution is a bugfix, try to [search](https://github.com/ktorio/ktor/issues)
if there is already an open ticket and open a new one if not yet opened.

Contributions are made using Github [pull requests](https://help.github.com/en/articles/about-pull-requests). 

1. [Create](https://github.com/ktorio/ktor/compare) a new PR, your PR should request to merge to the **master** branch.
2. Ensure that the description is clear, refer to an existing ticket/bug if applicable.
3. When contributing a new feature, provide motivation and use-cases describing why 
the feature is important enough to be delivered with ktor to everyone. 
4. Adding and updating features may require to update the [documentation](https://github.com/ktorio/ktorio.github.io).
Create a documentation PR and link both pull requests.
5. Make sure you have adequate tests added and no existing tests were broken. 

# Styleguides

Your code should conform to 
the official [Kotlin code style guide](https://kotlinlang.org/docs/reference/coding-conventions.html) 
except that star imports should be always enabled 
(ensure Preferences | Editor | Code Style | Kotlin, tab **Imports**, both `Use import with '*'` should be checked).

Every new source file should have a copyright header.

Every public API (including functions, classes, objects and so on) should be documented, 
every parameter, property, return types and exceptions should be described properly. 

Commit messages should be written in English only, should be clear and descriptive, 
written in present tense and using imperative mood ("Fix" instead of "Fixes", "Improve" instead of "Improved").
Add the related bug reference to a commit message (bug number after a hash character between round braces). 
See [How to Write a Git Commit Message](https://chris.beams.io/posts/git-commit/)

A questionable and new API should be marked with the `@KtorExperimentalAPI` annotation. 
A Public API that is not intended to be used by end-users that couldn't be made private/internal due to technical reasons,
should be marked with `@InternalAPI` annotation. 

# Code of conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
