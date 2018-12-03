def call() {
    sh 'which java'
    sh 'java -version'
    sh 'whoami'

    echo "GIT_URL: ${GIT_URL}"
    echo "GIT_URL_MODIFIED: ${GIT_URL_MODIFIED}"
    echo "BUILD_NUMBER ${BUILD_NUMBER}"
    echo "BUILD_ID ${BUILD_ID}"
    echo "BUILD_DISPLAY_NAME ${BUILD_DISPLAY_NAME}"
    echo "JOB_NAME ${JOB_NAME}"
    echo "JOB_BASE_NAME ${JOB_BASE_NAME}"
    echo "BUILD_TAG ${BUILD_TAG}"
    echo "EXECUTOR_NUMBER ${EXECUTOR_NUMBER}"
    echo "NODE_NAME ${NODE_NAME}"
    echo "NODE_LABELS ${NODE_LABELS}"
    echo "WORKSPACE ${WORKSPACE}"
    echo "JENKINS_HOME ${JENKINS_HOME}"
    echo "JENKINS_URL ${JENKINS_URL}"
    echo "BUILD_URL ${BUILD_URL}"
    echo "JOB_URL ${JOB_URL}"
    echo "CHANGE_AUTHOR_EMAIL ${GIT_COMMIT}"
    echo "AZURE_DEV_WESTEUROPE_DNS_PROP ${AZURE_DEV_WESTEUROPE_DNS_PROP}"
    echo "AZURE_SVC_HOSTNAME_PROP ${AZURE_SVC_HOSTNAME_PROP}"
    echo "GIT_SVC_ACOUNT_EMAIL_PROP ${GIT_SVC_ACOUNT_EMAIL_PROP}"
    echo "GIT_SVC_ACCOUNT_USER_PROP ${GIT_SVC_ACCOUNT_USER_PROP}"
    echo "NONPROD_WESTEUROPE_AZRGNAME_PROP ${NONPROD_WESTEUROPE_AZRGNAME_PROP}"
    echo "NONPROD_WESTEUROPE_AZACRNAME_PROP ${NONPROD_WESTEUROPE_AZACRNAME_PROP}"
    echo "NONPROD_WESTEUROPE_AZAKSCLUSTERNAME_PROP ${NONPROD_WESTEUROPE_AZAKSCLUSTERNAME_PROP}"
    echo "PROD_WESTEUROPE_AZRGNAME_PROP ${PROD_WESTEUROPE_AZRGNAME_PROP}"
    echo "PROD_WESTEUROPE_AZACRNAME_PROP ${PROD_WESTEUROPE_AZACRNAME_PROP}"
    echo "PROD_WESTEUROPE_AZAKSCLUSTERNAME_PROP ${PROD_WESTEUROPE_AZAKSCLUSTERNAME_PROP}"

    script {
        deploymentProperties = readProperties file:'deployment.properties'

        DEPLOY_TO_AWS       = deploymentProperties['DEPLOY_TO_AWS']

        AWS_DEV_REGION      = deploymentProperties['AWS_DEV_REGION'].split(',').collect{it as String}
        AWS_TEST_REGION     = deploymentProperties['AWS_TEST_REGION'].split(',').collect{it as String}
        AWS_PROD_REGION     = deploymentProperties['AWS_PROD_REGION'].split(',').collect{it as String}

        DEPLOY_TO_AZURE     = deploymentProperties['DEPLOY_TO_AZURE']

        AZURE_DEV_REGION    = deploymentProperties['AZURE_DEV_REGION'].split(',').collect{it as String}
        AZURE_TEST_REGION   = deploymentProperties['AZURE_TEST_REGION'].split(',').collect{it as String}
        AZURE_PROD_REGION   = deploymentProperties['AZURE_PROD_REGION'].split(',').collect{it as String}

        DEPLOY_TO_ON_PREM   = deploymentProperties['DEPLOY_TO_ON_PREM']
        ON_PREM_REGION      = deploymentProperties['ON_PREM_REGION']

        APIARY_PROJECT_NAME = deploymentProperties['APIARY_PROJECT_NAME']

        URI_ROOT_PATH       = deploymentProperties['URI_ROOT_PATH']

    }
}