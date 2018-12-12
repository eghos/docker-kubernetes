def call(Map pipelineParams) {

    pipeline {
        agent any

        parameters {
            string(name: 'DOCKER_ORG',                          defaultValue: 'apimgt',                                   description: 'Docker Repository user e.g. apimgt')
            string(name: 'DOCKER_REPO',                         defaultValue: 'dtrdev.hip.red.cdtapps.com',               description: 'Docker Repo URL e.g. dtrdev.hip.red.cdtapps.com')
            string(name: 'INTERNAL_SVC_HOSTNAME',               defaultValue: 'dev.eu-west-1.svc.hipint.red.cdtapps.com', description: 'AWS Ingress Internal Host Path e.g. dev.eu-west-1.svc.hipint.red.cdtapps.com')
        }

        environment {

            ORG                      = "${params.DOCKER_ORG}"
            DOCKER_REPO              = "${params.DOCKER_REPO}"
            INTERNAL_SVC_HOSTNAME    = "${params.INTERNAL_SVC_HOSTNAME}"

            BRANCH_NAME_FULL         = env.BRANCH_NAME.replace('', '')
            IMAGE_NAME               = """${sh (
                                            script: 'cat package.json | grep name | head -1 | awk -F: \'{ print $2 }\' | sed \'s/[",]//g\' | tr -d \'[[:space:]]\' ',
                                            returnStdout: true
                                       ).trim()}"""
            VERSION_FROM_PJ          = """${sh (
                                          script: 'cat package.json | grep version | head -1 | awk -F: \'{ print $2 }\' | sed \'s/[",]//g\' | tr -d \'[[:space:]]\' ',
                                          returnStdout: true
                                       ).trim()}"""
            DEV_SNAPSHOT_VERSION     = "1.0.${BUILD_NUMBER}-SNAPSHOT"
            RELEASE_NUMBER           = env.BRANCH_NAME.replace('release/', '')
            RELEASE_VERSION          = "${RELEASE_NUMBER}.RELEASE"

            GIT_URL_MODIFIED         = env.GIT_URL.replace('https://', 'git@').replace('com/', 'com:')

            AWS_DOCKER_TAG           = "${DOCKER_REPO}/${ORG}/${IMAGE_NAME}"
            DOCKER_ORG_IMAGE         = "${ORG}/${IMAGE_NAME}"

            JAVA_HOME                = "/usr/lib/jvm/java-10-oracle"
            JAVA_HOME8               = "/usr/lib/jvm/java-8-oracle"

            DEPLOY_TO_AWS            = ""
            DEPLOY_TO_AZURE          = ""
            DEPLOY_TO_ON_PREM        = ""

            IS_API_APPLICATION       = ""

            AZURE_SVC_HOSTNAME_PROP                  = cloudEnvironmentProps.getAzureSvcHostname()
            GIT_SVC_ACOUNT_EMAIL_PROP                = cloudEnvironmentProps.getGitSvcAccountEmail()
            GIT_SVC_ACCOUNT_USER_PROP                = cloudEnvironmentProps.getGitSvcAccountUser()
            PROD_WESTEUROPE_AZRGNAME_PROP            = cloudEnvironmentProps.getProdWesteuropeAzRgName()
            PROD_WESTEUROPE_AZACRNAME_PROP           = cloudEnvironmentProps.getProdWesteuropeAzAcrName()
            APIARY_IO_TOKEN_PROP                     = cloudEnvironmentProps.getApiaryIoToken()
            NPM_NEXUS_REPOSITORY_URL_PROP            = cloudEnvironmentProps.getNpmNexusRepositoryUrl()
        }

        tools {
          nodejs "latest-node"
        }

        stages {

            stage("Skip CICD?") {
                when {
                    expression {
                        result = sh (script: "git log -1 | grep '.*\\[ci skip\\].*'", returnStatus: true)
                        result == 0
                    }
                }
                steps {
                    stageSkipCICD()
                }
            }

            stage('Setup General') {
                steps {
                    stageSetupGeneral()
                    script {
                        deploymentProperties = readProperties file:'deployment.properties'

                        DEPLOY_TO_AWS       = deploymentProperties['DEPLOY_TO_AWS']

                        AWS_DEV_REGION      = deploymentProperties['AWS_DEV_REGION'].split(',').collect{it as String}
                        AWS_TEST_REGION     = deploymentProperties['AWS_TEST_REGION'].split(',').collect{it as String}
                        AWS_PPE_REGION      = deploymentProperties['AWS_PPE_REGION'].split(',').collect{it as String}
                        AWS_PROD_REGION     = deploymentProperties['AWS_PROD_REGION'].split(',').collect{it as String}

                        DEPLOY_TO_AZURE     = deploymentProperties['DEPLOY_TO_AZURE']

                        AZURE_DEV_REGION    = deploymentProperties['AZURE_DEV_REGION'].split(',').collect{it as String}
                        AZURE_TEST_REGION   = deploymentProperties['AZURE_TEST_REGION'].split(',').collect{it as String}
                        AZURE_PPE_REGION    = deploymentProperties['AZURE_PPE_REGION'].split(',').collect{it as String}
                        AZURE_PROD_REGION   = deploymentProperties['AZURE_PROD_REGION'].split(',').collect{it as String}

                        DEPLOY_TO_ON_PREM   = deploymentProperties['DEPLOY_TO_ON_PREM']
                        ON_PREM_REGION      = deploymentProperties['ON_PREM_REGION']

                        APIARY_PROJECT_NAME = deploymentProperties['APIARY_PROJECT_NAME']

                        URI_ROOT_PATH       = deploymentProperties['URI_ROOT_PATH']
                        KUBERNETES_NAMESPACE = deploymentProperties['KUBERNETES_NAMESPACE']
                        IS_API_APPLICATION  = deploymentProperties['IS_API_APPLICATION']
                    }
                }
            }

            stage('Update Versions') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        script {

                          CURRENT_VERSION = sh(returnStdout: true, script: 'cat package.json | grep version | head -1 | awk -F: \'{ print $2 }\' | sed \'s/[",]//g\' | tr -d \'[[:space:]]\'').trim()

                            if (env.BRANCH_NAME.startsWith("PR")) {
                                echo 'This is a PR Branch'
                            }

                            if (env.BRANCH_NAME.startsWith("develop")) {
                                echo 'This is a develop Branch'
                                //Update pom.xml version
                                sh "sed -i -e \"s|${CURRENT_VERSION}|${DEV_SNAPSHOT_VERSION}|g\" package.json"
                                DOCKER_VERSION = "${DEV_SNAPSHOT_VERSION}"
                            }

                            if (env.BRANCH_NAME.startsWith("release/")) {
                                echo 'This is a release Branch'
                                //Update pom.xml version
                                sh "sed -i -e \"s|${CURRENT_VERSION}|${RELEASE_VERSION}|g\" package.json"
                                DOCKER_VERSION = "${RELEASE_NUMBER}"

                            }

                            if (env.BRANCH_NAME.startsWith("master")) {
                                echo 'This is a master Branch'
                            }

                            if (env.BRANCH_NAME.startsWith("hotfix")) {
                                echo 'This is a hotfix Branch - TODO Inc Hotfix PATCH'
                            }
                        }
                        echo """${sh (
                                script: 'cat package.json | grep version | head -1 | awk -F: \'{ print $2 }\' | sed \'s/[",]//g\' | tr -d \'[[:space:]]\' ',
                                returnStdout: true
                              ).trim()}"""
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
                        sh 'npm install'
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
                        sh 'mocha'
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
                  nodejs(nodeJSInstallationName: 'latest-node', configId: 'eb9d09bd-11d9-4fbb-88e4-45b12cc7a19f') {
                    sh "npm publish --registry https://nexus.hip.red.cdtapps.com/repository/npm-internal/ "
                  }
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
                            AWS_DEV_REGION_MAP = AWS_DEV_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "dev")]
                            }
                            AWS_TEST_REGION_MAP = AWS_TEST_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "test")]
                            }
                            AWS_PPE_REGION_MAP = AWS_PPE_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "ppe")]
                            }
                            AWS_PROD_REGION_MAP = AWS_PROD_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "prod")]
                            }

                            AZURE_DEV_REGION_MAP = AZURE_DEV_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "prod")]
                            }
                            AZURE_TEST_REGION_MAP = AZURE_TEST_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "test")]
                            }
                            AZURE_PPE_REGION_MAP = AZURE_PPE_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "ppe")]
                            }
                            AZURE_PROD_REGION_MAP = AZURE_PROD_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "prod")]
                            }

                            sh "az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} -t ${AZURE_TENANT_ID}"
                            sh "az account set -s ${AZURE_SUBSCRIPTION_ID}"
                            sh "az acr login --name ${PROD_WESTEUROPE_AZACRNAME_PROP}"
                            ACRLOGINSERVER = sh(returnStdout: true, script: "az acr show --resource-group ${PROD_WESTEUROPE_AZRGNAME_PROP} --name ${PROD_WESTEUROPE_AZACRNAME_PROP} --query \"loginServer\" --output tsv").trim()
                            sh "docker build -t ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ."
                            sh "docker push ${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}"

                        }
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

            stage('Docker Deploy to PPE') {
                when {
                    changeRequest target: 'master'
                }
                steps {
                    echo "PR created to Master Branch. PPE Deployment will be performed in this stage."
                }
            }

            stage('Docker Deploy to PROD') {
                when {
                    anyOf {
                        branch 'master';
                        branch "hotfix/*"
                    }
                }
                steps {
                    echo 'Merge request to Master Branch has been approved. PROD Deployment will be performed in this stage.'
                }
            }

            stage('Commit Updated Version') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        withEnv(["GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no -o User=${GIT_SVC_ACCOUNT_USER_PROP} -i ${SSH_KEY}"]) {
                            script {
                                sh 'git remote rm origin'
                                //sh "git remote add origin git@git.build.ingka.ikea.com:IPIM-IP/${IMAGE_NAME}.git"
                                sh "git remote add origin ${GIT_URL_MODIFIED}"
                                sh 'git config --global user.email "l-apimgt-u-itsehbg@ikea.com"'
                                sh 'git config --global user.name "l-apimgt-u-itsehbg"'
                                sh 'git add package.json'
                                sh 'git commit -am "System - Update Package Version [ci skip]"'
                                sh 'git push origin "${BRANCH_NAME_FULL}" -f'
                            }
                        }
                    }
                }
            }

            stage('Clean Up') {
                steps {
                    cleanWs()
                }
            }
        }

        post {
            always {
                slackNotifier(currentBuild.currentResult)
            }
        }
    }
}

def generateAwsDeployStage(region, env) {
    return {
        stage("${env} - ${region}") {
            withCredentials(bindings: [usernamePassword(credentialsId: 'bc608fa5-71e6-4e08-b769-af3ca6024715', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh "docker login -u ${USERNAME} -p ${PASSWORD} ${DOCKER_REPO}"
                script {
                    sh "docker build -t ${AWS_DOCKER_TAG}:${DOCKER_VERSION} ."
                    sh "docker push ${AWS_DOCKER_TAG}:${DOCKER_VERSION}"
                }
                sh "docker logout ${DOCKER_REPO}"
                sh "docker login -u ${USERNAME} -p ${PASSWORD} ${DOCKER_REPO}"
                sh 'chmod +x ./build/*.yaml'
                sh """
                    cd build
                    export CONFIGMAP=configmap-${region}-${env}
                    export TARGET_HOST=aws
                    export DOCKER_VERSION=${DOCKER_VERSION}
                    cp configmap-${region}-${env}.yaml configmap-${region}-${env}-aws.yaml
                    cp deploy-service.yaml deploy-service-aws.yaml
                    cp ingress.yaml ingress-aws.yaml
                    sed -i -e \"s|IMAGE_NAME_VAR|${AWS_DOCKER_TAG}:${DOCKER_VERSION}|g\" deploy-service-aws.yaml
                    sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${INTERNAL_SVC_HOSTNAME}|g\" ingress-aws.yaml
                    cd ./${env}-ucp-bundle-admin
                    . ./env.sh
                    cd ..
                    . ./deploy.sh
                   """
                sh "docker logout ${DOCKER_REPO}"
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
                    sh 'chmod +x ./build/*.yaml'
                    sh """
                        cd build
                        export CONFIGMAP=configmap-${region}-${env}
                        export TARGET_HOST=azure
                        export DOCKER_VERSION=${DOCKER_VERSION}
                        export URI_ROOT_PATH_VAR=${URI_ROOT_PATH}
                        cp \"configmap-${region}-${env}.yaml\" \"configmap-${region}-${env}-azure.yaml\"
                        cp \"deploy-service.yaml\" \"deploy-service-azure.yaml\"
                        cp \"ingress.yaml\" \"ingress-azure.yaml\"
                        sed -i -e \"s|IMAGE_NAME_VAR|${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}|g\" deploy-service-azure.yaml
                        sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${AZ_ENV_REGION_SVC_HOSTNAME}|g\" ingress-azure.yaml
                        . ./deploy.sh
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