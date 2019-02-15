def call(Map pipelineParams) {

    pipeline {
        agent any

        environment {

            BRANCH_NAME_FULL         = env.BRANCH_NAME.replace('', '')
            IMAGE_NAME               = "fxrateapi"
            DEV_SNAPSHOT_VERSION     = "1.0.${BUILD_NUMBER}-SNAPSHOT"
//            RELEASE_NUMBER           = env.BRANCH_NAME.replace('release/', '')tmp
            RELEASE_NUMBER           = "1.0.0"
            RELEASE_VERSION          = "${RELEASE_NUMBER}.RELEASE"

            GIT_URL_MODIFIED         = env.GIT_URL.replace('https://', 'git@').replace('com/', 'com:')

            DEPLOY_TO_AWS            = ""
            DEPLOY_TO_AZURE          = ""
            DEPLOY_TO_ON_PREM        = ""

            IS_API_APPLICATION       = ""

            AZURE_SVC_HOSTNAME_PROP                  = cloudEnvironmentProps.getAzureSvcHostname()
            AWS_SVC_HOSTNAME_PROP                    = cloudEnvironmentProps.getAwsSvcHostname()
            GIT_SVC_ACOUNT_EMAIL_PROP                = cloudEnvironmentProps.getGitSvcAccountEmail()
            GIT_SVC_ACCOUNT_USER_PROP                = cloudEnvironmentProps.getGitSvcAccountUser()
            PROD_WESTEUROPE_AZRGNAME_PROP            = cloudEnvironmentProps.getProdWesteuropeAzRgName()
            PROD_WESTEUROPE_AZACRNAME_PROP           = cloudEnvironmentProps.getProdWesteuropeAzAcrName()
            APIARY_IO_TOKEN_PROP                     = cloudEnvironmentProps.getApiaryIoToken()
            APIARY_IO_DREDD_PROP                     = cloudEnvironmentProps.getApiaryDreddToken()
            NPM_NEXUS_REPOSITORY_URL_PROP            = cloudEnvironmentProps.getNpmNexusRepositoryUrl()
            DOCKER_IMAGE_ORG_PROP                    = cloudEnvironmentProps.getDockerImageOrg()
            AZURE_PROD_SUBSCRIPTION_ID_PROP          = cloudEnvironmentProps.getAzureProdSubscriptionId()
            AZURE_LOWER_ENV_SUBSCRIPTION_ID_PROP     = cloudEnvironmentProps.getAzureLowerEnvSubscriptionId()
            AWS_CONTAINER_REPOSITORY_URL_PROP        = cloudEnvironmentProps.getAwsContainerRepositoryUrl()

            DOCKER_ORG_IMAGE         = "${DOCKER_IMAGE_ORG_PROP}/${IMAGE_NAME}"
        }

        stages {

            stage("Skip CICD Dev?") {
                when {
                    allOf {
                        branch "develop*";
                        expression {
                            result = sh(script: "git log -1 | grep '.*\\[ci skip dev\\].*'", returnStatus: true)
                            result == 0
                        }
                    }
                }
                steps {
                    stageSkipCICD()
                }
            }

            stage("Skip CICD Release?") {
                when {
                    allOf {
                        branch "release/*";
                        expression {
                            result = sh(script: "git log -1 | grep '.*\\[ci skip release\\].*'", returnStatus: true)
                            result == 0
                        }
                    }
                }
                steps {
                    stageSkipCICD()
                }
            }


            stage('Setup General') {
                steps {
                    withCredentials([azureServicePrincipal('sp-ipim-ip-aks')]) {
//                        stageSetupGeneral()
                        script {
                            //Get variables from project deployment.properties
                            deploymentProperties = readProperties file: './build/deployment.properties'

                            //Collect AWS Deployment variables
                            DEPLOY_TO_AWS = deploymentProperties['DEPLOY_TO_AWS']
                            AWS_DEV_REGION    = deploymentProperties['AWS_DEV_REGION'].split(',').collect { it as String }
                            AWS_TEST_REGION   = deploymentProperties['AWS_TEST_REGION'].split(',').collect { it as String }
                            AWS_PPE_REGION    = deploymentProperties['AWS_PPE_REGION'].split(',').collect { it as String }
                            AWS_PROD_REGION   = deploymentProperties['AWS_PROD_REGION'].split(',').collect { it as String }

                            //Collect Azure Deployment variables
                            DEPLOY_TO_AZURE   = deploymentProperties['DEPLOY_TO_AZURE']
                            AZURE_DEV_REGION  = deploymentProperties['AZURE_DEV_REGION'].split(',').collect { it as String }
                            AZURE_TEST_REGION = deploymentProperties['AZURE_TEST_REGION'].split(',').collect {it as String }
                            AZURE_PPE_REGION  = deploymentProperties['AZURE_PPE_REGION'].split(',').collect { it as String }
                            AZURE_PROD_REGION = deploymentProperties['AZURE_PROD_REGION'].split(',').collect { it as String }

                            //Collect On Prem Deployment variables
                            DEPLOY_TO_ON_PREM = deploymentProperties['DEPLOY_TO_ON_PREM']
                            ON_PREM_REGION    = deploymentProperties['ON_PREM_REGION']

                            //Collect Deployment related variables
                            APIARY_PROJECT_NAME  = deploymentProperties['APIARY_PROJECT_NAME']
                            URI_ROOT_PATH        = deploymentProperties['URI_ROOT_PATH']
                            KUBERNETES_NAMESPACE = deploymentProperties['KUBERNETES_NAMESPACE']
                            IS_API_APPLICATION   = deploymentProperties['IS_API_APPLICATION']
                            SERVICE_VERSION      = deploymentProperties['SERVICE_VERSION']
                            AWS_CLUSTER_NAME     = deploymentProperties['AWS_CLUSTER_NAME']

                            //Set up AWS deployment region map properties
                            AWS_DEV_REGION_MAP = AWS_DEV_REGION.collectEntries {
                                ["${it}": generateAwsDeployStage(it, "dev")]
                            }
                            AWS_TEST_REGION_MAP = AWS_TEST_REGION.collectEntries {
                                ["${it}": generateAwsDeployStage(it, "test")]
                            }
                            AWS_PPE_REGION_MAP = AWS_PPE_REGION.collectEntries {
                                ["${it}": generateAwsDeployStage(it, "ppe")]
                            }
                            AWS_PROD_REGION_MAP = AWS_PROD_REGION.collectEntries {
                                ["${it}": generateAwsDeployStage(it, "prod")]
                            }

                            //Set up Azure deployment region map properties
                            AZURE_DEV_REGION_MAP = AZURE_DEV_REGION.collectEntries {
                                ["${it}": generateAzureDeployStage(it, "dev")]
                            }
                            AZURE_TEST_REGION_MAP = AZURE_TEST_REGION.collectEntries {
                                ["${it}": generateAzureDeployStage(it, "test")]
                            }
                            AZURE_PPE_REGION_MAP = AZURE_PPE_REGION.collectEntries {
                                ["${it}": generateAzureDeployStage(it, "ppe")]
                            }
                            AZURE_PROD_REGION_MAP = AZURE_PROD_REGION.collectEntries {
                                ["${it}": generateAzureDeployStage(it, "prod")]
                            }

                            //Log into ACR/ECR etc
                            logIntoAzure()
                        }
                    }
                }
            }

            stage('Update Versions') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        script {

                            if (env.BRANCH_NAME.startsWith("PR")) {
                                echo 'This is a PR Branch'
                            }

                            if (env.BRANCH_NAME.startsWith("develop")) {
                                echo 'This is a develop Branch'
                                DOCKER_VERSION = "${DEV_SNAPSHOT_VERSION}"
                            }

                            if (env.BRANCH_NAME.startsWith("release/")) {
                                echo 'This is a release Branch'
                                DOCKER_VERSION = "${RELEASE_NUMBER}"
                            }

                            if (env.BRANCH_NAME.startsWith("master")) {
                                echo 'This is a master Branch'
                            }

                            if (env.BRANCH_NAME.startsWith("hotfix")) {
                                echo 'This is a hotfix Branch - TODO Inc Hotfix PATCH'
                            }

                        }
                    }
                }
            }

            stage('Code Build') {
                when {
                    anyOf {
                        branch "develop*";
                        branch "PR*"
                        branch "release/*"
                        branch "hotfix/*"
                    }
                }
                steps {
                    withCredentials(bindings: [usernamePassword(credentialsId: 'bc608fa5-71e6-4e08-b769-af3ca6024715', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        echo "TODO"
                    }
                }
            }

            stage('Code Test') {
                when {
                    anyOf {
                        branch "develop*";
                        branch "PR*"
                        branch "release/*"
                        branch "hotfix/*"
                    }
                }
                steps {
                    withCredentials(bindings: [usernamePassword(credentialsId: 'bc608fa5-71e6-4e08-b769-af3ca6024715', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                        echo "TODO"
                    }
                }
            }

            stage('Code Deploy to Nexus') {
               when {
                   anyOf {
                       branch "develop*";
                       branch "release/*"
                   }
               }
               steps {
                  echo "TODO"
               }
            }

//            stage('Docker Build') {
//                when {
//                    anyOf {
//                        branch "develop*";
//                        branch "release/*";
//                    }
//                }
//                steps {
//                    withCredentials([azureServicePrincipal('sp-ipim-ip-aks')]) {
//                        script {
//                            sh "docker build -t ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ."
//                            sh "docker push ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}"
//                        }
//                        sh """
//                           mkdir -p ~/.aws
//                           cp ./build/aws/credentials ~/.aws/credentials
//                           cp ./build/aws/config ~/.aws/config
//                           export AWS_PROFILE=ikea-tools-system
//                           \$(aws ecr get-login --no-include-email --region eu-west-1)
//
//                           docker tag ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
//                           docker push ${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
//                           """
//                    }
//                }
//            }

            stage('Docker Build') {
                when {
                    anyOf {
                        branch "develop*";
                        branch "release/*";
                    }
                }
                steps {
                    withCredentials([azureServicePrincipal('sp-ipim-ip-aks')]) {
                        script {
                            //Build Docker image for Azure
                            sh "docker build -t ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ."
                            //Push Docker image to ACR.
                            sh "docker push ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}"

                            //Openshift
                            //export TOKEN=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJvY3AtcGlwZWxpbmVzLWlwaW0taXAiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiamVua2lucy10b2tlbi1tMjVkZyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJqZW5raW5zIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQudWlkIjoiM2EzNjhjOTktZTk4MS0xMWU4LTgyZTQtMDA1MDU2ODUxMmM3Iiwic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50Om9jcC1waXBlbGluZXMtaXBpbS1pcDpqZW5raW5zIn0.DUM8sW_mhH67NEHSa854qyrdSwmDWPqqCw6yCF5Wg1vkWM3wgpndfHMHbi5ULW2RkghqwrBzO0RCAFAcOW38AwGoqkcOtmlEgBQN5z_9qoXcQw00ze8EkPz0paVDV4Qw1NJ6iI0Z6mYlZNV8OdUKkySPvu4kRDJdqNL20xBnJLkc1Zx2Rh_OfJXtcSutqm2FHBEIzadM_kAezhr_4Awj4YP5aLdosQUqYHi9C4UBdggTrTQpYV-2A3LbNZ0VHYHqG6y6k5XD8hPKOFZFQvlU1jATjk5FG50KCbqyIzUAeoPq7tGI26rTsXYLS_d4sW4-BMwRGYbDzFPIBXrSXoXWng
                            sh "~/oc/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit/./oc login --token eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJvY3AtcGlwZWxpbmVzLWlwaW0taXAiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiamVua2lucy10b2tlbi1tMjVkZyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJqZW5raW5zIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQudWlkIjoiM2EzNjhjOTktZTk4MS0xMWU4LTgyZTQtMDA1MDU2ODUxMmM3Iiwic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50Om9jcC1waXBlbGluZXMtaXBpbS1pcDpqZW5raW5zIn0.DUM8sW_mhH67NEHSa854qyrdSwmDWPqqCw6yCF5Wg1vkWM3wgpndfHMHbi5ULW2RkghqwrBzO0RCAFAcOW38AwGoqkcOtmlEgBQN5z_9qoXcQw00ze8EkPz0paVDV4Qw1NJ6iI0Z6mYlZNV8OdUKkySPvu4kRDJdqNL20xBnJLkc1Zx2Rh_OfJXtcSutqm2FHBEIzadM_kAezhr_4Awj4YP5aLdosQUqYHi9C4UBdggTrTQpYV-2A3LbNZ0VHYHqG6y6k5XD8hPKOFZFQvlU1jATjk5FG50KCbqyIzUAeoPq7tGI26rTsXYLS_d4sW4-BMwRGYbDzFPIBXrSXoXWng ocm-02.ikeadt.com:8443 --insecure-skip-tls-verify"

                            sh "docker login -p eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJvY3AtcGlwZWxpbmVzLWlwaW0taXAiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlY3JldC5uYW1lIjoiamVua2lucy10b2tlbi1tMjVkZyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50Lm5hbWUiOiJqZW5raW5zIiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQudWlkIjoiM2EzNjhjOTktZTk4MS0xMWU4LTgyZTQtMDA1MDU2ODUxMmM3Iiwic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50Om9jcC1waXBlbGluZXMtaXBpbS1pcDpqZW5raW5zIn0.DUM8sW_mhH67NEHSa854qyrdSwmDWPqqCw6yCF5Wg1vkWM3wgpndfHMHbi5ULW2RkghqwrBzO0RCAFAcOW38AwGoqkcOtmlEgBQN5z_9qoXcQw00ze8EkPz0paVDV4Qw1NJ6iI0Z6mYlZNV8OdUKkySPvu4kRDJdqNL20xBnJLkc1Zx2Rh_OfJXtcSutqm2FHBEIzadM_kAezhr_4Awj4YP5aLdosQUqYHi9C4UBdggTrTQpYV-2A3LbNZ0VHYHqG6y6k5XD8hPKOFZFQvlU1jATjk5FG50KCbqyIzUAeoPq7tGI26rTsXYLS_d4sW4-BMwRGYbDzFPIBXrSXoXWng -u unused docker-registry-default.ocp-02.ikeadt.com"

                            sh "docker tag ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} docker-registry-default.ocp-02.ikeadt.com/sandbox-ipim-ip/${DOCKER_OPENSHIFT_IMAGE}:${DOCKER_VERSION}"
                            sh "docker push docker-registry-default.ocp-02.ikeadt.com/sandbox-ipim-ip/${DOCKER_OPENSHIFT_IMAGE}:${DOCKER_VERSION}"

                            //Add the following lines back under export AWS_PROFILE once aws cli issue is resolved on new jenkins vm
                            //  \$(aws ecr get-login --no-include-email --region eu-west-1)
//                            docker tag ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
//                            docker push ${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
                        }
                        sh """
                           mkdir -p ~/.aws
                           cp ./build/aws/credentials ~/.aws/credentials
                           cp ./build/aws/config ~/.aws/config
                           export AWS_PROFILE=ikea-tools-system

                           """
                    }
                }
            }

            stage ('DEV Deploy - OnPrem OpenShift') {
                when {
                    allOf {
                        branch "develop*";
                        expression { DEPLOY_TO_ON_PREM_OPENSHIFT == 'true' }
                    }
                }
                steps {
                    script {
                        sh "~/oc/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit/./oc project ${OPENSHIFT_DEV}"

                        //sh "~/oc/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit/./oc apply -f ./build/istio/configmap-os-onprem-dev.yaml"
                        sh "~/oc/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit/./oc new-app --image=${DOCKER_OPENSHIFT_IMAGE}:${DOCKER_VERSION} --name=${DOCKER_OPENSHIFT_IMAGE}"
                        sh "~/oc/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit/./oc create route edge ${DOCKER_OPENSHIFT_IMAGE}-route --service=${DOCKER_OPENSHIFT_IMAGE}"
                    }
                }
            }

            stage ('DEV Deploy - AWS') {
                when {
                    allOf {
                        branch "develop*";
                        expression { DEPLOY_TO_AWS == 'true' }
                    }
                }
                steps {
                    executeDeploy(AWS_DEV_REGION_MAP)
                }
            }

            stage ('DEV Deploy - Azure') {
                when {
                    allOf {
                        branch "develop*";
                        expression { DEPLOY_TO_AZURE == 'true' }
                    }
                }
                steps {
                    sh "az account set -s ${AZURE_LOWER_ENV_SUBSCRIPTION_ID_PROP}"
                    executeDeploy(AZURE_DEV_REGION_MAP)
                }
            }

            stage ('TEST Deploy - AWS') {
                when {
                    allOf {
                        branch "release/*";
                        expression { DEPLOY_TO_AWS == 'true' }
                    }
                }
                steps {
                    executeDeploy(AWS_TEST_REGION_MAP)
                }
            }

            stage ('TEST Deploy - Azure') {
                when {
                    allOf {
                        branch "release/*";
                        expression { DEPLOY_TO_AZURE == 'true' }
                    }
                }
                steps {
                    sh "az account set -s ${AZURE_LOWER_ENV_SUBSCRIPTION_ID_PROP}"
                    executeDeploy(AZURE_TEST_REGION_MAP)
                }
            }

            stage('Service Tests') {
                when {
                    branch "release/*"
                }
                parallel {
                    stage('Service Test') {
                        steps {
                            sh 'echo'
                        }
                    }
                    stage('Contract-Test') {
                        steps {
                            sh 'echo'
                        }
                    }
                    stage('Functional-Test') {
                        steps {
                            sh 'echo'
                        }
                    }
                    stage('Security-Test') {
                        steps {
                            sh 'echo'
                        }
                    }
                    stage('Load-Test') {
                        steps {
                            sh 'echo'
                        }
                    }
                }
            }

            stage('PPE Deploy - AWS') {
                when {
                    allOf {
                        changeRequest target: 'master'
                        expression { DEPLOY_TO_AWS == 'true' }
                    }
                }
                steps {
                    echo "PR created to Master Branch. PPE Deployment will be performed in this stage."
                    script {
                        DOCKER_VERSION = "${RELEASE_NUMBER}"
                    }
                    executeDeploy(AWS_PPE_REGION_MAP)
                }
            }

            stage('PPE Deploy - Azure') {
                when {
                    allOf {
                        changeRequest target: 'master'
                        expression { DEPLOY_TO_AZURE == 'true' }
                    }
                }
                steps {
                    echo "PR created to Master Branch. PPE Deployment will be performed in this stage."
                    script {
                        DOCKER_VERSION = "${RELEASE_NUMBER}"
                    }
                    sh "az account set -s ${AZURE_LOWER_ENV_SUBSCRIPTION_ID_PROP}"
                    executeDeploy(AZURE_PPE_REGION_MAP)
                }
            }

            stage('PROD Deploy Release - Azure') {
                when {
                    allOf {
                        branch 'master';
                        expression {DEPLOY_TO_AZURE == 'true'}
                    }
                }
                steps {
                    echo 'Merge request to Master Branch has been approved. PROD Deployment will be performed in this stage.'
                    script {
                        DOCKER_VERSION = "${RELEASE_NUMBER}"
                    }
                    sh "az account set -s ${AZURE_PROD_SUBSCRIPTION_ID_PROP}"
                    executeDeploy(AZURE_PROD_REGION_MAP)
                }
            }

            stage('PROD Deploy Release - AWS') {
                when {
                    allOf {
                        branch 'master';
                        expression { DEPLOY_TO_AWS == 'true' }
                    }
                }
                steps {
                    echo 'Merge request to Master Branch has been approved. PROD Deployment will be performed in this stage.'
                    script {
                        DOCKER_VERSION = "${RELEASE_NUMBER}"
                    }
                    executeDeploy(AWS_PROD_REGION_MAP)
                }
            }

            stage('PROD Deploy HotFix - Azure') {
                when {
                    allOf {
                        branch "hotfix/*"
                        expression {DEPLOY_TO_AZURE == 'true'}
                    }
                }
                steps {
                    echo 'HotFix change has been implemented. PROD Deployment will be performed in this stage.'
                    script {
                        DOCKER_VERSION = "${RELEASE_NUMBER}"
                    }
                    sh "az account set -s ${AZURE_PROD_SUBSCRIPTION_ID_PROP}"
                    executeDeploy(AZURE_PROD_REGION_MAP)
                }
            }

            stage('PROD Deploy HotFix - AWS') {
                when {
                    allOf {
                        branch "hotfix/*"
                        expression {DEPLOY_TO_AWS == 'true'}
                    }
                }
                steps {
                    echo 'HotFix change has been implemented. PROD Deployment will be performed in this stage.'
                    script {
                        DOCKER_VERSION = "${RELEASE_NUMBER}"
                    }
                    executeDeploy(AWS_PROD_REGION_MAP)
                }
            }


            stage('Commit Updated Version') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        withEnv(["GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no -o User=${GIT_SVC_ACCOUNT_USER_PROP} -i ${SSH_KEY}"]) {
                            script {
                                echo "TODO"
                                // sh 'git remote rm origin'
                                // //sh "git remote add origin git@git.build.ingka.ikea.com:IPIM-IP/${IMAGE_NAME}.git"
                                // sh "git remote add origin ${GIT_URL_MODIFIED}"
                                // sh 'git config --global user.email "l-apimgt-u-itsehbg@ikea.com"'
                                // sh 'git config --global user.name "l-apimgt-u-itsehbg"'
                                // sh 'git add package.json'
                                // sh 'git commit -am "System - Update Package Version [ci skip]"'
                                // sh 'git push origin "${BRANCH_NAME_FULL}" -f'
                            }
                        }
                    }
                }
            }
        }

        post {
            always {
                slackNotify(currentBuild.currentResult)
                cleanWs()
            }
        }
    }
}

def logIntoAzure(){
    //Log into ACR/ECR etc
//    sh "az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} -t ${AZURE_TENANT_ID}"
    sh "az login --service-principal -u aed28a46-e479-40ad-92f1-14e723c2f8f4 -p yi1ACwcv4myWis8fKsH1cQJL1whLPqJcZDCN1RSukCQ= -t 720b637a-655a-40cf-816a-f22f40755c2c"
//    sh "az account set -s ${AZURE_SUBSCRIPTION_ID}"
    //Use Prod Subscription ID
    sh "az account set -s ${AZURE_PROD_SUBSCRIPTION_ID_PROP}"
    sh "az acr login --name ${PROD_WESTEUROPE_AZACRNAME_PROP}"
    ACRLOGINSERVER = sh(returnStdout: true, script: "az acr show --resource-group ${PROD_WESTEUROPE_AZRGNAME_PROP} --name ${PROD_WESTEUROPE_AZACRNAME_PROP} --query \"loginServer\" --output tsv").trim()
}

def generateAwsDeployStage(region, env) {
    return {
        stage("${env} - ${region}") {
            script {
//                sh 'mkdir -p ~/.aws'
//                sh 'cp ./build/aws/credentials ~/.aws/credentials'
//                sh 'cp ./build/aws/config ~/.aws/config'
//                sh "export AWS_PROFILE=eks@ikea-${env}"
//                AWS_DEPLOY_EKS_CLUSTER_NAME = sh(returnStdout: true, script: "aws ssm get-parameter --name /ipimip/cluster/<the_cluster_tag>/name --query 'Parameter.Value' --output text").trim()
//                AWS_DEPLOY_EKS_CLUSTER_NAME = sh(returnStdout: true, script: "aws ssm get-parameter --name /ipimip/cluster/cluster1/name --query 'Parameter.Value' --output text").trim()
//                sh "aws eks update-kubeconfig --kubeconfig awskubeconfig --name ${AWS_DEPLOY_EKS_CLUSTER_NAME}"
//                sh "aws eks update-kubeconfig --kubeconfig awskubeconfig --name cluster1 --region eu-west-1"

                if ("${env}".startsWith("prod")) {
                    ENV_LATEST = "inter"
                } else {
                    ENV_LATEST = "${env}"
                }

                AWS_ENV_REGION_SVC_HOSTNAME = "${AWS_SVC_HOSTNAME_PROP}".replace('<ENV>', "${ENV_LATEST}").replace('<REGION>', "${region}")

                //To generate te docker login command, need to use  $(aws ecr get-login --no-include-email --region eu-west-1)
                //docker tag acrweprod01.azurecr.io/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} 603698310563.dkr.ecr.eu-west-1.amazonaws.com/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
                //docker push 603698310563.dkr.ecr.eu-west-1.amazonaws.com/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
                sh 'chmod +x ./build/istio/*.yaml'
                sh """
                        mkdir -p ~/.aws
                        cp ./build/aws/credentials ~/.aws/credentials
                        cp ./build/aws/config ~/.aws/config                       

                        export AWS_PROFILE=eks@ikea-${env}-${region}
                        aws eks update-kubeconfig --kubeconfig ./build/aws/awskubeconfig --name eksipimip
                        chmod +x ./build/aws/aws-iam-authenticator
                        
                        ls -ltr ./build/aws

                        sed -i -e \"s|aws-iam-authenticator|./aws-iam-authenticator|g\" ./build/aws/awskubeconfig

                        cd build/istio
                        cp \"configmap-aws-${region}-${env}.yaml\" \"configmap-aws-${region}-${env}-aws.yaml\"
                        cp \"deploy-service.yaml\" \"deploy-service-aws.yaml\"
                        cp \"virtual-service.yaml\" \"virtual-service-aws.yaml\"
                        cp \"destination-rule.yaml\" \"destination-rule-aws.yaml\"
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" configmap-aws-${region}-${env}-aws.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" configmap-aws-${region}-${env}-aws.yaml
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f configmap-aws-${region}-${env}-aws.yaml

                        sed -i -e \"s|IMAGE_NAME_VAR|${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}|g\" deploy-service-aws.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" deploy-service-aws.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" deploy-service-aws.yaml
                        sed -i -e \"s|CONFIGMAP_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}-configmap|g\" deploy-service-aws.yaml
                        sed -i -e \"s|VERSION_VAR|${DOCKER_VERSION}|g\" deploy-service-aws.yaml
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f deploy-service-aws.yaml
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${AWS_ENV_REGION_SVC_HOSTNAME}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|SVC_PATH_VAR|${URI_ROOT_PATH}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|ENV_VAR|${ENV_LATEST}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|REGION_VAR|${region}|g\" virtual-service-aws.yaml
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f virtual-service-aws.yaml
                        
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" destination-rule-aws.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" destination-rule-aws.yaml 
                        sed -i -e \"s|LABEL_APP_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" destination-rule-aws.yaml                       
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f destination-rule-aws.yaml
                       """
            }
        }
    }
}

def generateAzureDeployStage(region, env) {
    return {
        stage("${env} - ${region}") {
            withCredentials([azureServicePrincipal('sp-ipim-ip-aks')]) {
                script {
                    AZ_DEPLOY_RG_NAME = sh(returnStdout: true, script: "az group list --query \"[?tags.Env=='${env}' && tags.Region=='${region}'].{name:name}\" --output tsv").trim()
                    AZ_DEPLOY_AKS_CLUSTER_NAME = sh(returnStdout: true, script: "az resource list --query \"[?tags.Env=='${env}' && tags.Region=='${region}' && tags.Cluster=='default' && tags.ServiceType=='aks'].{name:name}\" --output tsv").trim()

                    if ("${env}".startsWith("prod")) {
                        ENV_LATEST = "inter"
                    } else {
                        ENV_LATEST = "${env}"
                    }

                    AZ_ENV_REGION_SVC_HOSTNAME = "${AZURE_SVC_HOSTNAME_PROP}".replace('<ENV>', "${ENV_LATEST}").replace('<REGION>', "${region}")
                    sh "az aks get-credentials --resource-group=${AZ_DEPLOY_RG_NAME} --name=${AZ_DEPLOY_AKS_CLUSTER_NAME}"
                    sh 'chmod +x ./build/istio/*.yaml'
                    sh """
                        cd build/istio
                        cp \"configmap-az-${region}-${env}.yaml\" \"configmap-az-${region}-${env}-azure.yaml\"
                        cp \"deploy-service.yaml\" \"deploy-service-azure.yaml\"
                        cp \"virtual-service.yaml\" \"virtual-service-azure.yaml\"
                        cp \"destination-rule.yaml\" \"destination-rule-azure.yaml\"
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" configmap-az-${region}-${env}-azure.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" configmap-az-${region}-${env}-azure.yaml
                        kubectl apply -f configmap-az-${region}-${env}-azure.yaml

                        sed -i -e \"s|IMAGE_NAME_VAR|${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}|g\" deploy-service-azure.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" deploy-service-azure.yaml 
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" deploy-service-azure.yaml
                        sed -i -e \"s|CONFIGMAP_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}-configmap|g\" deploy-service-azure.yaml
                        sed -i -e \"s|VERSION_VAR|${DOCKER_VERSION}|g\" deploy-service-azure.yaml
                        kubectl apply -f deploy-service-azure.yaml
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${AZ_ENV_REGION_SVC_HOSTNAME}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|SVC_PATH_VAR|${URI_ROOT_PATH}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|ENV_VAR|${ENV_LATEST}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|REGION_VAR|${region}|g\" virtual-service-azure.yaml
                        kubectl apply -f virtual-service-azure.yaml
                        
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" destination-rule-azure.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" destination-rule-azure.yaml 
                        sed -i -e \"s|LABEL_APP_VAR|${IMAGE_NAME}-${SERVICE_VERSION}|g\" destination-rule-azure.yaml                       
                        kubectl apply -f destination-rule-azure.yaml
                       """
                }
            }
        }
    }
}

void executeDeploy(Map inboundMap) {

    def mapValues = inboundMap.values();

    for (customStage in mapValues) {
        script customStage
    }

}

def slackNotify(String buildResult) {
    JOB_URL_HTTPS = env.BUILD_URL.replace('http', 'https')

    if (buildResult == "SUCCESS") {
        slackSend color: "good", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} was Successful!\n Build URL: ${JOB_URL_HTTPS}"
    } else if (buildResult == "FAILURE") {
        slackSend color: "danger", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} has Failed!\n Build URL: ${JOB_URL_HTTPS}"
    } else if (buildResult == "UNSTABLE") {
        slackSend color: "warning", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} was Unstable!\n Build URL: ${JOB_URL_HTTPS}"
    } else if (buildResult == "ABORTED") {
    } else {
        slackSend color: "danger", message: "Job: ${env.JOB_NAME} with Build Number ${env.BUILD_NUMBER} - its result was unclear. Please investigate!\n Build URL: ${JOB_URL_HTTPS}"
    }
}