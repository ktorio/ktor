Pod::Spec.new do |spec|
    spec.name                     = 'ktor_client_webrtc'
    spec.version                  = '3.4.0-SNAPSHOT'
    spec.homepage                 = 'https://github.com/ktorio/ktor'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'Ktor WebRTC Client'
    spec.vendored_frameworks      = 'build/cocoapods/framework/KtorWebRTC.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '13.0'
    spec.dependency 'WebRTC-SDK', '137.7151.04'
                
    if !Dir.exist?('build/cocoapods/framework/KtorWebRTC.framework') || Dir.empty?('build/cocoapods/framework/KtorWebRTC.framework')
        raise "

        Kotlin framework 'KtorWebRTC' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :ktor-client-webrtc:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':ktor-client-webrtc',
        'PRODUCT_MODULE_NAME' => 'KtorWebRTC',
    }
                
    spec.script_phases = [
        {
            :name => 'Build ktor_client_webrtc',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
                
end