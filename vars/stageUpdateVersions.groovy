def call() {
    sh 'chmod +x ./mvnw'
    script {
        if (env.BRANCH_NAME.startsWith("PR")) {
            echo 'This is a PR Branch'
//                                sh './mvnw -B org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules -DnewVersion=${DEV_PR_VERSION}'
        }

        if (env.BRANCH_NAME.startsWith("develop")) {
            echo 'This is a develop Branch'
            //Update pom.xml version
            sh './mvnw -B org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules -DnewVersion=1.0.${BUILD_NUMBER}-SNAPSHOT'
            DOCKER_VERSION = "${DEV_SNAPSHOT_VERSION}"
        }

        if (env.BRANCH_NAME.startsWith("release/")) {
            echo 'This is a release Branch'
            //Update pom.xml version
            sh './mvnw -B org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules -DnewVersion=${RELEASE_VERSION}'
            DOCKER_VERSION = "${RELEASE_NUMBER}"

        }

        if (env.BRANCH_NAME.startsWith("master")) {
            echo 'This is a master Branch'
        }

        if (env.BRANCH_NAME.startsWith("hotfix")) {
            echo 'This is a hotfix Branch - TODO Inc Hotfix PATCH'
        }
    }
    echo readMavenPom().getVersion()
}