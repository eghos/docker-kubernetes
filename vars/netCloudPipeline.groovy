def call(Map pipelineParams) {

    pipeline {
        agent any

        environment {

            BRANCH_NAME_FULL         = env.BRANCH_NAME.replace('', '')
            IMAGE_NAME               = "fxrateapi"
            DEV_SNAPSHOT_VERSION     = "1.0.${BUILD_NUMBER}-SNAPSHOT"
//            RELEASE_NUMBER           = env.BRANCH_NAME.replace('release/', '')
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
                        stageSetupGeneral()
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
                            sh "docker build -t ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ."
                            sh "docker push ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}"
                        }
                        sh """
                           mkdir -p ~/.aws
                           cp ./build/aws/credentials ~/.aws/credentials
                           cp ./build/aws/config ~/.aws/config
                           export AWS_PROFILE=ikea-tools-system

docker login -u AWS -p eyJwYXlsb2FkIjoiMGNRWHVSNXVjVUs3TkxaWWdRdGIzdllSOFlwVU12MksyU3pkVDBPL2VVSkZDYTJ3MGJaSk9oNGtFZlBTU0FEL21lbFphM2ZXYzUrMVZkNHNBV1RTOGh0TkdBakpmYUoyWDBGa3BnSjU3WFZGQjM0L3lPeE9UT0M2RzMrVGw4dW5la3lyYVRoVmxNVW5wQ1BmcU9TRlMzK0k0VHA2WjdDeDE3NE5RSVF0NUpqd0c5b2hYNEh5cFpDbmZ2UVF5UllZUFZIMHVWRVVuMWxuTFM0N3JkZEFncm1aWXZpMHNZejhPbENHNE5tZlcrU1Q5TElQZ05ISVdYS3BDY3RhZ3VKTmJjMkRUNXo0dm1TU2N1WnBtTTBrRnlJZFR4Qnhhbnd6MWJaQ0krSms4MlNLMFMwRk5wTThZWCtFaENUWWxaU3NOclprZXlpNDVLYkxzb3Yzd0FCRHRib1Z1anBVVjZCR09ueGdNZ3pYb1Q0ekxBN2RoUmI4dGUzNUFOQlY2Yll3WExuS3B4cHVHTzk0VGdlMXFrN252R1NZbUJNRzhnejFuYi80YUpsZis2S3hvWkJSZDNJc0NITitJVSs0UUViMnM4L0RzNEZ2T25LRDB6YWtCZE1lWXc2U1A2Q0RpcS9ZNlVLS21udENubnFuQldkZmNkandsWmNOdEpiU1lZeWluOGN3Mlo1cG94U3lKWGJnZWQ5cEgwRkRZR1ZabHF2cC8vVmFRNFhLQXlLUzVnNVkxanEySVVIL3hHMjZ5QzE3bUpDZlJzTFBVZ01lNm9Zem9xT0pPS29YTll1WWtyZVRJTG1qMTF2bUQ5NUo2eDI4KzBSZ0JUWWVCaVViSThPRm0wV0k3MjNnVGZSRU5jQWNEcTlmYTVPMWduNW15RDZlM25aTWVpY1Rsb2VaWldhZXZ0WHVHMmZpa1hZZXdCejFLYkNSclZGanNqUU8xajZ0RmNnMzk3VXVWSTFzTUVYcTZ1eHFjajJKMUJ2QTVxa25Mb3JCcU1SQmJTWE5LaE9GRVdSeDh4aEdHVEprMVM3KzQwNHdzdnc3NGw4MUV3MTAyZzFTSCtJZmpXTktvczNjWFBqeDBsdkg5b0p6cUwxVHR1NVNTVWZvdGdtMjhSRTZZRDdzTlBOajhiazcraUQzWG02SUcrZVBrNmhCS1lsalRBSDBCcDhtV3FSaEs4WU5EeS96bVREWVJwOC82U0RkeXEvaStGQ2ZKQXVQdlJHVnpOMGlPVG9Canl0dCtDcWJuWUIyaGI2ZTc5ZTZCK0lVcHRlY3lVYnIrM01oK1JMVHN6ejhsT3RGT1NBWXkzS1JvRDFoL294VkJuRXk3UGtWdW5TY2VJWDFwUDBUSHNLU0xuUGtFd3pCUUtGSTJlc2pXZk9mMktpd2J6RER4TnFROXFaYWVkMjlsUEI4NEtjeFFlYzQ2VTd3dHBnR1ZvQzl1NXpRSVNnSjIvMFpXQlR0WGk0ZERkVkMiLCJkYXRha2V5IjoiQVFFQkFIaCtkUytCbE51ME54blh3b3diSUxzMTE1eWpkK0xOQVpoQkxac3VuT3hrM0FBQUFINHdmQVlKS29aSWh2Y05BUWNHb0c4d2JRSUJBREJvQmdrcWhraUc5dzBCQndFd0hnWUpZSVpJQVdVREJBRXVNQkVFREduaWM5ZVRXZnJoUlVvb1l3SUJFSUE3Y2xORVJmRXkyNHhhU0gyNFhTQ3J1NXdJUVY0NzBiNmUwWnNQbE5UbmFDNEcxOXI5TWtuWFFsSW4yaTJ6NUNUaUFqNUlkQjFWSXE3NjFtbz0iLCJ2ZXJzaW9uIjoiMiIsInR5cGUiOiJEQVRBX0tFWSIsImV4cGlyYXRpb24iOjE1NDk3NjU5NjJ9 https://318063795105.dkr.ecr.eu-west-1.amazonaws.com

                           docker tag ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
                           docker push ${AWS_CONTAINER_REPOSITORY_URL_PROP}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
                           """
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
                        DOCKER_VERSION = ${RELEASE_NUMBER}
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
                        DOCKER_VERSION = ${RELEASE_NUMBER}
                    }
                    sh "az account set -s ${AZURE_LOWER_ENV_SUBSCRIPTION_ID_PROP}"
//                    executeDeploy(AZURE_PPE_REGION_MAP)
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
                        DOCKER_VERSION = ${RELEASE_NUMBER}
                    }
                    sh "az account set -s ${AZURE_PROD_SUBSCRIPTION_ID_PROP}"
//                    executeDeploy(AZURE_PROD_REGION_MAP)
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
                        DOCKER_VERSION = ${RELEASE_NUMBER}
                    }
                    executeDeploy(AWS_PPE_REGION_MAP)
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
                        DOCKER_VERSION = ${RELEASE_NUMBER}
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
                        DOCKER_VERSION = ${RELEASE_NUMBER}
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
                slackNotifier(currentBuild.currentResult)
                cleanWs()
            }
        }
    }
}

def logIntoAzure(){
    //Log into ACR/ECR etc
    sh "az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} -t ${AZURE_TENANT_ID}"
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

                AWS_ENV_REGION_SVC_HOSTNAME = "${AWS_SVC_HOSTNAME_PROP}".replace('<ENV>', "${env}").replace('<REGION>', "${region}")

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
                        sed -i -e \"s|ENV_VAR|${env}|g\" virtual-service-aws.yaml
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
                    AZ_ENV_REGION_SVC_HOSTNAME = "${AZURE_SVC_HOSTNAME_PROP}".replace('<ENV>', "${env}").replace('<REGION>', "${region}")
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
                        sed -i -e \"s|ENV_VAR|${env}|g\" virtual-service-azure.yaml
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