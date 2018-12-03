def call() {
    stage("Skip CICD?") {
        steps {
            script {
                echo 'Got ci=skip, aborting build'
                currentBuild.result = 'ABORTED'
                error('CI-Skip')
            }
        }
    }
}