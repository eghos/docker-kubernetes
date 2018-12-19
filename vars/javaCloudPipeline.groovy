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
            IMAGE_NAME               = readMavenPom().getArtifactId()
            VERSION_FROM_POM         = readMavenPom().getVersion()
            DEV_SNAPSHOT_VERSION     = "1.0.${BUILD_NUMBER}-SNAPSHOT"
            RELEASE_NUMBER           = env.BRANCH_NAME.replace('release/', '')
            RELEASE_VERSION          = "${RELEASE_NUMBER}.RELEASE"
            PROD_RELEASE_NUMBER      = readMavenPom().getVersion().replace('.RELEASE', '')

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
            APIARY_IO_DREDD_PROP                     = cloudEnvironmentProps.getApiaryDreddToken()
            NPM_NEXUS_REPOSITORY_URL_PROP            = cloudEnvironmentProps.getNpmNexusRepositoryUrl()
        }

        tools {
            nodejs "latest-node"
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

                            APIARY_PROJECT_NAME = deploymentProperties['APIARY_PROJECT_NAME']
                            URI_ROOT_PATH = deploymentProperties['URI_ROOT_PATH']
                            KUBERNETES_NAMESPACE = deploymentProperties['KUBERNETES_NAMESPACE']
                            IS_API_APPLICATION = deploymentProperties['IS_API_APPLICATION']

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
                        sh 'chmod +x ./mvnw'
                        script {
                            if (env.BRANCH_NAME.startsWith("PR")) {
                                echo 'This is a PR Branch'
                            }

                            if (env.BRANCH_NAME.startsWith("develop")) {
                                echo 'This is a develop Branch'
                                //Update pom.xml version
                                // sh './mvnw -B org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules -DnewVersion=1.0.${BUILD_NUMBER}-SNAPSHOT'
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
                                DOCKER_VERSION = "${PROD_RELEASE_NUMBER}"
                            }

                            if (env.BRANCH_NAME.startsWith("hotfix")) {
                                echo 'This is a hotfix Branch - TODO Inc Hotfix PATCH'
                                sh './mvnw -B org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules -DnewVersion=${RELEASE_VERSION}'
                                DOCKER_VERSION = "${RELEASE_NUMBER}"
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
                        sh 'chmod +x ./mvnw'
                        sh './mvnw -B -T 4 -fae -f pom.xml -Dmaven.test.skip=true clean install'
                    }
                }
            }

//             stage('Code Test') {
//                 when {
//                     anyOf {
//                         branch "develop*";
//                         branch "PR*"
//                         branch "release/*"
//                         branch "hotfix/*"
//                     }
//                 }
//                 steps {
//                     withCredentials(bindings: [usernamePassword(credentialsId: 'bc608fa5-71e6-4e08-b769-af3ca6024715', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
//                         sh 'chmod +x ./mvnw'
//                         sh './mvnw -f pom.xml test'
//                         //sh './mvnw -f pom.xml sonar:sonar -Dsonar.login=$USERNAME -Dsonar.password=$PASSWORD'
//
////                         sh ''' export JAVA_HOME=$JAVA_HOME8
////           cd build
////           ./mvnw -f ../pom.xml sonar:sonar -Dsonar.login=d4e0a890f5606a8df6c5ef32ed480abc611b6a7a   -Dsonar.projectKey=ipimip.${IMAGE_NAME}.dev'''
//
//                         //d4e0a890f5606a8df6c5ef32ed480abc611b6a7a = staging2
//                         //2af9224b1068533d1c48def794f022f2df1b928e = staging
//
//                         //aws instance 7.2
//                         sh './mvnw -f pom.xml sonar:sonar -Dsonar.login=$USERNAME -Dsonar.password=$PASSWORD'
//
//                     }
//                 }
//             }

            stage('Code Deploy to Nexus') {
                when {
                    anyOf {
                        branch "develop*";
                        branch "release/*"
                    }
                }
                steps {
                    sh 'chmod +x ./mvnw'
                    sh './mvnw -f pom.xml -Dmaven.test.skip=true deploy'
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

            stage('Fetch API Blueprint & Run Dredd Test') {
                when {
                    anyOf {
                        changeRequest target: 'develop*'
                        branch "develop*";
                    }
                }
                steps {
                    //Install Dependencies
                    sh 'node -v'
                    sh 'npm -v'
                    sh 'npm install'
                    sh 'npm -g install dredd@stable'
                    //Firstly fetch the latest API Blueprint Definition.
                    script {
                        sh """
                           cd ./build/api-blueprint
                           export APIARY_API_KEY=${APIARY_IO_TOKEN_PROP}
                           apiary fetch --api-name ${APIARY_PROJECT_NAME} --output ${APIARY_PROJECT_NAME}.apib
                           """
                        sh "git add ./build/api-blueprint/${APIARY_PROJECT_NAME}.apib"
                    }
                    script {
                        AZ_ENV_REGION_SVC_HOSTNAME = "${AZURE_SVC_HOSTNAME_PROP}".replace('<ENV>', "dev").replace('<REGION>', "westeurope")
                        //Make copy of dredd-template (to stop git automatically checking in existing modified file
                        sh 'cp ./build/api-blueprint/dredd-template.yml ./build/api-blueprint/dredd.yml'
                        //Replace variables in Dredd file
                        sh """
                       cd build/api-blueprint
                       sed -i -e \"s|APIARY_PROJECT_VAR|${APIARY_PROJECT_NAME}.apib|g\" dredd.yml
                       sed -i -e \"s|SERVICE_GATEWAY_DNS_VAR|${AZ_ENV_REGION_SVC_HOSTNAME}|g\" dredd.yml
                       """
                    }
                    //Run Dredd Test against APIB Definition and running service.
                    script {
                        try {
                            sh """
                                  export APIARY_API_KEY=${APIARY_IO_DREDD_PROP}
                                  export APIARY_API_NAME=${APIARY_PROJECT_NAME}
                                  dredd --config ./build/api-blueprint/dredd.yml
                               """
                        } catch (err) {
                            //TODO
                            echo 'Dredd Test failed. Continuing with pipeline'
                        }
                    }
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
                    allOf {
                        branch "release/*";
                        expression { IS_API_APPLICATION == 'true' }
                    }
                }
                parallel {
                    stage('API Fortress Tests') {
                        steps {
                            sh 'echo'
                        }
                    }
                    stage('Dredd Tests (API Contract)') {
                        steps {
                            //Fetch API Definition from apiary.io using apiary CLI.
                            script {
                                sh """
                                   cd ./build
                                   export APIARY_API_KEY=${APIARY_IO_TOKEN_PROP}
                                   apiary fetch --api-name ${APIARY_PROJECT_NAME} --output ${APIARY_PROJECT_NAME}.apib
                                   """
                                sh "git add ./build/${APIARY_PROJECT_NAME}.apib"
                            }
                            //Make copy of dredd-template (to stop git automatically checking in existing modified file
                            sh 'cp ./build/dredd-template.yml ./build/dredd.yml'
                            //Replace variables in Dredd file
                            sh """
                               cd build
                               sed -i -e \"s|APIARY_PROJECT_VAR|${APIARY_PROJECT_NAME}.apib|g\" dredd.yml
                               sed -i -e \"s|SERVICE_GATEWAY_DNS_VAR|${SERVICE_GATEWAY_DNS_PROP}${URI_ROOT_PATH}|g\" dredd.yml
                               """
                            //Run Dredd test.
                            script {
                                try {
                                    sh 'docker run -i -v ${WORKSPACE}/build:/api -w /api apiaryio/dredd'
                                    sh "git add ./build/results.xml"
                                } catch (err) {
                                    //sh 'chmod +x ./build/results.xml'
                                    sh 'cd ./build && ls -lart'
                                    //sh "git add ./build/results.xml"
                                    echo 'Get XUnit/JUnit Results if available'
                                    //junit './build/results.xml'
                                }
                            }
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
                        DOCKER_VERSION = "${PROD_RELEASE_NUMBER}"
                    }

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
                    executeDeploy(AZURE_PROD_REGION_MAP)
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
                    executeDeploy(AZURE_PROD_REGION_MAP)
                }
            }

            stage('Commit Changes') {
                when {
                    anyOf {
                        branch 'develop*';
                        branch "release/*"
                        branch "hotfix/*"
                    }
                }
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        withEnv(["GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no -o User=${GIT_SVC_ACCOUNT_USER_PROP} -i ${SSH_KEY}"]) {
                            script {
                                sh 'git remote rm origin'
                                sh "git remote add origin ${GIT_URL_MODIFIED}"
                                sh 'git config --global user.email "l-apimgt-u-itsehbg@ikea.com"'
                                sh 'git config --global user.name "l-apimgt-u-itsehbg"'
                                sh 'git add pom.xml'
                                sh 'git status'

                                try {
                                    if (env.BRANCH_NAME.startsWith("develop")) {
                                        sh 'git commit -m "System - CICD Pipeline changes committed for Development. [ci skip dev]"'
                                    }

                                    if (env.BRANCH_NAME.startsWith("release/")) {
                                        sh 'git commit -m "System - CICD Pipeline changes committed for Release. [ci skip release]"'
                                    }

                                    sh 'git push origin "${BRANCH_NAME_FULL}" -f'
                                } catch (err){
                                    echo 'Git Commit/Push was not successful (Nothing to Commit and Push)'
                                }
                            }
                        }
                    }
                }
            }

        }

        post {
            always {
                cleanWs()
                slackNotifier(currentBuild.currentResult)
            }
        }

    }
}

def generateAwsDeployStage(region, env) {
    return {
        stage("${region}") {
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
                        cp \"configmap-az-${region}-${env}.yaml\" \"configmap-az-${region}-${env}-azure.yaml\"
                        cp \"deploy-service.yaml\" \"deploy-service-azure.yaml\"
                        cp \"virtual-service.yaml\" \"virtual-service-azure.yaml\"
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" configmap-az-${region}-${env}-azure.yaml
                        kubectl apply -f configmap-az-${region}-${env}-azure.yaml

                        sed -i -e \"s|IMAGE_NAME_VAR|${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}|g\" deploy-service-azure.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}|g\" deploy-service-azure.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" deploy-service-azure.yaml
                        sed -i -e \"s|CONFIGMAP_NAME_VAR|${IMAGE_NAME}-configmap|g\" deploy-service-azure.yaml
                        sed -i -e \"s|VERSION_VAR|${DOCKER_VERSION}|g\" deploy-service-azure.yaml
                        kubectl apply -f deploy-service-azure.yaml
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${AZ_ENV_REGION_SVC_HOSTNAME}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|SVC_PATH_VAR|${URI_ROOT_PATH}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|ENV_VAR|${env}|g\" virtual-service-azure.yaml
                        sed -i -e \"s|REGION_VAR|${region}|g\" virtual-service-azure.yaml
                        kubectl apply -f virtual-service-azure.yaml
                       """
                }
            }
        }
    }
}

def logIntoAzure(){
    //Log into ACR/ECR etc
    sh "az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} -t ${AZURE_TENANT_ID}"
    sh "az account set -s ${AZURE_SUBSCRIPTION_ID}"
    sh "az acr login --name ${PROD_WESTEUROPE_AZACRNAME_PROP}"
    ACRLOGINSERVER = sh(returnStdout: true, script: "az acr show --resource-group ${PROD_WESTEUROPE_AZRGNAME_PROP} --name ${PROD_WESTEUROPE_AZACRNAME_PROP} --query \"loginServer\" --output tsv").trim()
}

void executeDeploy(Map inboundMap) {

    def mapValues = inboundMap.values();

    for (customStage in mapValues) {
        script customStage
    }
}