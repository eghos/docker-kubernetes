def call() {
    stage("Skip CICD?") {
        when {
            expression {
                result = sh(script: "git log -1 | grep '.*\\[ci skip\\].*'", returnStatus: true)
                result == 0
            }
        }
        steps {
            script {
                echo 'Got ci=skip, aborting build'
                currentBuild.result = 'ABORTED'
                error('CI-Skip')
            }
        }
    }
}