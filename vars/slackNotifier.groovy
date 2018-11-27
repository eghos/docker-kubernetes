#!/usr/bin/env groovy

def call(String buildResult) {
  JOB_URL_HTTPS = env.BUILD_URL.replace('http','https')

  if ( buildResult == "SUCCESS" ) {
    slackSend color: "good", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} by ${env.USER_ID} was Successful!\n Build URL: ${JOB_URL_HTTPS}"
  }
  else if( buildResult == "FAILURE" ) { 
    slackSend color: "danger", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} by ${env.USER_ID} has Failed!"
  }
  else if( buildResult == "UNSTABLE" ) {
    slackSend color: "warning", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} by ${env.USER_ID} was Unstable!"
  }
  else if( buildResult == "ABORTED" ) {
  }
  else {
    slackSend color: "danger", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} by ${env.USER_ID} - its result was unclear. Please investigate!\n Build URL at: ${env.BUILD_URL}"
  }
}
