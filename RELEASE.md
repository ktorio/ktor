# ktor release plan

1. Ensure ktor samples run configurations are green:
    - https://teamcity.jetbrains.com/viewType.html?buildTypeId=KotlinTools_Ktor_SamplesMasterKtor&branch_KotlinTools_Ktor=%3Cdefault%3E&tab=buildTypeStatusDiv
    - https://teamcity.jetbrains.com/viewType.html?buildTypeId=KotlinTools_Ktor_Samples&branch_KotlinTools_Ktor=%3Cdefault%3E&tab=buildTypeStatusDiv
1. Prepare ktor https://github.com/ktorio/ktor
    - Update [CHANGELOG.md], add new version's section and changelog    
    - Edit `gradle.properties`, remove `-SNAPSHOT` suffix
    - Commit & push
    - Prepare release branch, cherry-pick/merge required commits (optional)
1. Prepare https://github.com/ktorio/ktorio.github.io
    - Create release branch
    - Edit `_config.yml`: update `ktor_version` and `kotlin_version`
    - Add a page at `quickstart/migration` (optional)
1. Prepare https://github.com/ktorio/ktor-init-tools
    - Create a branch
    - Edit `ktor-generator/src/commonMain/kotlin/io/ktor/start/Versions.kt`
    - Check `ktor-generator/src/commonMain/kotlin/io/ktor/start/features/client/ClientFeatures.kt`
    - Check `ktor-generator/src/commonMain/kotlin/io/ktor/start/features/server/`
    - Once added any new features, add it here: `ktor-generator/src/commonMain/kotlin/io/ktor/start/features/all.kt`
    - Edit `gradle.properties` `version`
    - Run `./synchronize_versions.kt` (optional)
        - Otherwise edit `ktor-intellij-plugin/resources/META-INF/plugin.xml`
            * update `version` attribute
            * update changelog section
        - and update `ktor-generator-website/resources/index.html` as well
    - Run `git status` and review your changes, revert `docs` removals if appeared
    - Run `./gradlew :ktor-generator-website:buildAndCopy`
    - Run `./gradlew :ktor-intellij-plugin:build`
    - Run `git status` again to review changes
    - Run `git add -A` & `git commit -m "Upgrade ktor to [new-version]"`
1. Ensure TC builds for the branch are green including MacOS
    - https://teamcity.jetbrains.com/project.html?projectId=KotlinTools_Ktor&tab=projectOverview
1. Open `Deploy (Prepare)` settings:
    - https://teamcity.jetbrains.com/admin/editBuildParams.html?id=buildType:KotlinTools_Ktor_DeployPrepare
    - edit `release.version` property
1. Run `Deploy (Train)` 
    - https://teamcity.jetbrains.com/viewType.html?buildTypeId=KotlinTools_Ktor_DeployTrain
    - select required branch if needed
    - Click "Deploy" button
    - Alternative way is to click `...` of Deploy button and select branch right there in the dialog
    - Observe build train progress: all `Deploy (Prepare)`, `Deploy (Mac)`, `Deploy (Linux)` and finally `Deploy (Train)` should be completed
        * if failed see bintray section beyond and do reject artifacts instead of publish, be careful and select particular version first before clicking reject to not reject irrelevant files
1. Open https://bintray.com/
    - Do sign in.
    - Open https://bintray.com/kotlin/ktor/ktor
    - Observe new appeared version at right side, click on version
    - Click `Files` to ensure that everything looks fine
    - Click `Publish` (`Reject` and then return back to a previous step to retry)
    - Go back to https://bintray.com/kotlin/ktor/ktor and wait for twitter's icon appears at new version
    - Open version's page again and click `Maven Central`
    - Enter your login/password at synchronization form, keep checkbox marked, click "Sync" button
    - Wait for synchronization completion, may take several minutes 
    - At completion ensure "Sync status" at right side shows `Last Sync Status: Successfully synced and closed repo.` and it corresponds to your actual date
1. Prepare https://github.com/ktorio/api.ktor.io
    - ensure that you have jekyll installed
    - run `./build_doc.sh [new version]` and ensure that the corresponding directory has been created
    - run `./sync.kt` or edit `assets/versions.js` and `latest/index.html` manually
    - edit `./build_all.sh` and append new version line
    - run `git status` and quickly review changes
    - run `git add -A` or `git add [new-version] assets/versions.js latest/index.html` 
    - run `git commit -m "Upgrade to [new-version]"`
    - run `git push`
1. Return back to https://github.com/ktorio/ktorio.github.io
    - Merge your branch into master: `git checkout`  `git merge --ff-only [your-branch]` `git push`
1. Open http://repo1.maven.org/maven2/io/ktor/ktor-http/ 
    - wait for your new version appearance (may take half an hour or more)  
1. Return back to https://github.com/ktorio/ktor-init-tools
    - Merge your branch into master
1. Open https://plugins.jetbrains.com/plugin/10823-ktor
    - Sign in under your jetbrains account
    - Click "Update plugin" button
    - Select a zip file from your `ktor-init-tools/ktor-intellij-plugin/build/distributions/ktor-***.zip`
    - Fill "Change notes"
    - Click "Upload new build"
1. Open ktor-init-tools local repo
    - Edit `gradle.properties` to the next snapshot version
    - Run `./synchronize_versions.kt` (optional)
    - Edit `ktor-intellij-plugin/resources/META-INF/plugin.xml`, update version attribute as well
    - Edit `ktor-generator-website/resources/index.html` if `./synchronize_versions.kt` wasn't applied
    - Commit & push
1. Open your local `ktor` repo
    - Edit `gradle.properties` to the next snapshot version (with `-SNAPSHOT` suffix)
    - Commit & push
    - Copy release notes from [CHANGELOG.md]
1. Open https://github.com/ktorio/ktor/releases
    - Find tag corresponding to your new version and click at it
    - Click "Edit tag"
    - Fill release title with your version, e.g. `1.0.1`
    - Paste changelog
    - Click "Publish release"
1. Open slack channel https://kotlinlang.slack.com/messages/C0A974TJ9
    - Post a new message with `:mega: ktor [your version] has been released` with the changelog and a link to migration guide (if required)
    - Click "More options" at your post and apply "Pin to #ktor"
1. Open https://github.com/ktorio/ktor/issues
    - Check every changelog's issue and close it with comment containig ktor version
    - Open https://github.com/ktorio/ktor/milestones
        - if there is a milestone corresponding to your new version, review open tickets and reassign them if necessary
        - close the corresponding milestone
1. Open https://github.com/ktorio/ktorio.github.io/deployments
    - ensure that "Deployed to github-pages" contains latest commit and everything green
    - click "view deployment"
    - ensure that the website is up and running, check blue version badge at the page bottom
1. Open https://github.com/ktorio/ktor-init-tools/deployments
    - ensure that "Deployed to github-pages" contains latest commit and everything green
    - click "view deployment"
    - check that there is the new ktor version in the versions list
1. Open https://github.com/ktorio/api.ktor.io/deployments
    - ensure that "Deployed to github-pages" contains latest commit and everything green
    - click "view deployment"
    - ensure that kdoc appeared for the right version
        - if it get stuck at infinite reload then simply close and try later
        - if doesn't work after a while then check that all steps at the corresponding step are completed right
1. Edit Slack's channel topic: `kotlin-libs-releases`:
    - https://jetbrains.slack.com/messages/CE12EC2FN
1. Update Libraries registry quip document:
    - https://jetbrains.quip.com/d4bbAEUIvQe0/Libraries-registry
1. Update this document if something is not exactly right or unclear   

