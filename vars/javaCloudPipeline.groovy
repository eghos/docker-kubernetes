def call(Map pipelineParams) {

    pipeline {
        agent any

        parameters {
            string(name: 'REGION',                   defaultValue: 'ireland',                                  description: 'Target region deployment e.g. ireland, virginia')
            string(name: 'DOCKER_ORG',               defaultValue: 'apimgt',                                   description: 'Docker Repository user e.g. apimgt')
            string(name: 'DOCKER_REPO',              defaultValue: 'dtrdev.hip.red.cdtapps.com',               description: 'Docker Repo URL e.g. dtrdev.hip.red.cdtapps.com')
            string(name: 'SVC_PATH',                 defaultValue: 'price-service',                            description: 'Ingress Service Path e.g. testapi')
            string(name: 'INTERNAL_SVC_HOSTNAME',    defaultValue: 'dev.eu-west-1.svc.hipint.red.cdtapps.com', description: 'AWS Ingress Internal Host Path e.g. dev.eu-west-1.svc.hipint.red.cdtapps.com')
            string(name: 'AZ_INTERNAL_SVC_HOSTNAME', defaultValue: 'dev-az-svc.westeurope.cloudapp.azure.com', description: 'Azure Ingress Internal Host Path e.g. dev-az-svc.westeurope.cloudapp.azure.com')
            string(name: 'KUBERNETES_NAMESPACE',     defaultValue: 'default',                                  description: 'The Kubernetes namespace for the service e.g. default')
            string(name: 'AZRGNAME',                 defaultValue: 'ipimip-dev-westEurope-rg',                 description: 'Azure region name')
            string(name: 'AZACRNAME',                defaultValue: 'acrwedevgupuy7',                           description: 'Azure container registry')
            string(name: 'AZAKSCLUSTERNAME',         defaultValue: 'akswedevgupuy7',                           description: 'Azure Kubernetes cluster name')
            string(name: 'GIT_SVC_ACOUNT_EMAIL',     defaultValue: 'l-apimgt-u-itsehbg@ikea.com',              description: 'GitHub Service Account Email')
            string(name: 'GIT_SVC_ACCOUNT_USER',     defaultValue: 'l-apimgt-u-itsehbg',                       description: 'GitHub Service Account Name')
        }

        environment {
            ORG =                      "${params.DOCKER_ORG}"
            DOCKER_REPO =              "${params.DOCKER_REPO}"
            INTERNAL_SVC_HOSTNAME =    "${params.INTERNAL_SVC_HOSTNAME}"
            AZ_INTERNAL_SVC_HOSTNAME = "${params.AZ_INTERNAL_SVC_HOSTNAME}"
            SVC_PATH =                 "${params.SVC_PATH}"
            KUBERNETES_NAMESPACE =     "${params.KUBERNETES_NAMESPACE}"

            BRANCH_NAME_FULL =      env.BRANCH_NAME.replace('', '')
            IMAGE_NAME =            readMavenPom().getArtifactId()
            VERSION_FROM_POM =      readMavenPom().getVersion()
            DEV_SNAPSHOT_VERSION =  "1.0.${BUILD_NUMBER}-SNAPSHOT"
            RELEASE_NUMBER =        env.BRANCH_NAME.replace('release/', '')
            RELEASE_VERSION =       "${RELEASE_NUMBER}.RELEASE"

            DEPLOY_TO_AWS     =     ""
            DEPLOY_TO_AZURE   =     ""
            DEPLOY_TO_ON_PREM =     ""

            AWS_DOCKER_TAG =        "${DOCKER_REPO}/${ORG}/${IMAGE_NAME}"
            DOCKER_ORG_IMAGE =      "${ORG}/${IMAGE_NAME}"

            GIT_EMAIL =             "${GIT_SVC_ACOUNT_EMAIL}"
            GIT_USER =              "${GIT_SVC_ACCOUNT_USER}"
            GIT_URL =               env.GIT_URL.replace('https://', 'git@')

            JAVA_HOME =             "/usr/lib/jvm/java-10-oracle"
            JAVA_HOME8 =            "/usr/lib/jvm/java-8-oracle"

            AZ_ACR_NAME =           "${params.AZACRNAME}"
            AZ_AKS_CLUSTER_NAME =   "${params.AZAKSCLUSTERNAME}"
            AZ_RG_NAME =            "${params.AZRGNAME}"
        }

        stages {

            stage("CI Skip?") {
                when {
                    expression {
                        result = sh (script: "git log -1 | grep '.*\\[ci skip\\].*'", returnStatus: true)
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

            stage('Setup') {
                steps {
                    sh 'which java'
                    sh 'java -version'
                    sh 'whoami'

                    echo "GIT URL: ${GIT_URL}"
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

                    script {
                        deploymentProperties = readProperties file:'deployment.properties'

                        DEPLOY_TO_AWS = deploymentProperties['DEPLOY_TO_AWS']

                        AWS_DEV_REGION = deploymentProperties['AWS_DEV_REGION'].split(',').collect{it as String}
                        AWS_TEST_REGION = deploymentProperties['AWS_TEST_REGION'].split(',').collect{it as String}
                        AWS_PROD_REGION = deploymentProperties['AWS_PROD_REGION'].split(',').collect{it as String}

                        DEPLOY_TO_AZURE = deploymentProperties['DEPLOY_TO_AZURE']

                        AZURE_DEV_REGION = deploymentProperties['AZURE_DEV_REGION'].split(',').collect{it as String}
                        AZURE_TEST_REGION = deploymentProperties['AZURE_TEST_REGION'].split(',').collect{it as String}
                        AZURE_PROD_REGION = deploymentProperties['AZURE_PROD_REGION'].split(',').collect{it as String}

                        DEPLOY_TO_ON_PREM = deploymentProperties['DEPLOY_TO_ON_PREM']
                        ON_PREM_REGION = deploymentProperties['ON_PREM_REGION']

                    }
                }
            }

            stage('Update Versions') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        sh 'chmod +x ./mvnw'
                        script {
                            if (env.BRANCH_NAME.startsWith("PR")) {
                                echo 'This is a PR Branch'
                                //Update pom.xml version - In Dev branch, this is used to ensure JAR artefact has correct Version when pushed to Nexus.
                                sh './mvnw -B org.codehaus.mojo:versions-maven-plugin:2.5:set -DprocessAllModules -DnewVersion=${DEV_PR_VERSION}'
                            }

                            if (env.BRANCH_NAME.startsWith("develop")) {
                                echo 'This is a develop Branch'
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
                        sh 'chmod +x ./build/mvnw'
                        sh './mvnw -f pom.xml test'
                        //sh './mvnw -f pom.xml sonar:sonar -Dsonar.login=$USERNAME -Dsonar.password=$PASSWORD'

                        sh ''' export JAVA_HOME=$JAVA_HOME8
          cd build
          ./mvnw -f ../pom.xml sonar:sonar -Dsonar.login=d4e0a890f5606a8df6c5ef32ed480abc611b6a7a   -Dsonar.projectKey=ipimip.${IMAGE_NAME}.dev'''

                        //d4e0a890f5606a8df6c5ef32ed480abc611b6a7a = staging2
                        //2af9224b1068533d1c48def794f022f2df1b928e = staging

                        //aws instance 7.2
                        //sh './mvnw -f ../pom.xml sonar:sonar -Dsonar.login=$USERNAME -Dsonar.password=$PASSWORD'

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
                            AWS_DEV_REGION_MAP = AWS_DEV_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "dev")]
                            }
                            AWS_TEST_REGION_MAP = AWS_TEST_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "test")]
                            }
                            AWS_PROD_REGION_MAP = AWS_PROD_REGION.collectEntries {
                                ["${it}" : generateAwsDeployStage(it, "prod")]
                            }

                            AZURE_DEV_REGION_MAP = AZURE_DEV_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "dev")]
                            }
                            AZURE_TEST_REGION_MAP = AZURE_TEST_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "test")]
                            }
                            AZURE_PROD_REGION_MAP = AZURE_PROD_REGION.collectEntries {
                                ["${it}" : generateAzureDeployStage(it, "prod")]
                            }

                            sh 'az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} -t ${AZURE_TENANT_ID}'
                            sh 'az account set -s ${AZURE_SUBSCRIPTION_ID}'
                            sh 'az acr login --name ${AZ_ACR_NAME}'
                            sh 'az aks get-credentials --resource-group=${AZ_RG_NAME} --name=${AZ_AKS_CLUSTER_NAME}'
                            ACR_LOGIN_SERVER = sh(returnStdout: true, script: 'az acr show --resource-group ${AZ_RG_NAME} --name ${AZ_ACR_NAME} --query "loginServer" --output tsv').trim()
                            sh "docker build -t ${ACR_LOGIN_SERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} ."
                            sh "docker push ${ACR_LOGIN_SERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}"

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
                    script {
                        parallel AWS_DEV_REGION_MAP
                    }
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
                    script {
                        parallel AZURE_DEV_REGION_MAP
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
                    script {
                        parallel AWS_TEST_REGION_MAP
                    }
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
                    script {
                        parallel AZURE_TEST_REGION_MAP
                    }
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

            stage('Commit POM') {
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        withEnv(["GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no -o User=${GIT_USER} -i ${SSH_KEY}"]) {
                            sh 'git remote rm origin'
                            sh 'git remote add origin "git@git.build.ingka.ikea.com:IPIM-IP/${SVC_PATH}.git"'
                            //sh 'git remote add origin "${GIT_URL}"'

                            sh 'git config --global user.email "l-apimgt-u-itsehbg@ikea.com"'
                            sh 'git config --global user.name "l-apimgt-u-itsehbg"'
                            sh 'git add pom.xml'
                            sh 'git commit -am "System - Update POM Version [ci skip]"'
                            sh 'git push origin "${BRANCH_NAME_FULL}"'
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
        stage("${region}") {
            withCredentials(bindings: [usernamePassword(credentialsId: 'bc608fa5-71e6-4e08-b769-af3ca6024715', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                sh "docker login -u ${USERNAME} -p ${PASSWORD} ${DOCKER_REPO}"
                script {
                    sh "docker build -t ${AWS_DOCKER_TAG}:${DOCKER_VERSION} ."
                    sh "docker push ${AWS_DOCKER_TAG}:${DOCKER_VERSION}"
                }
                sh "docker logout ${DOCKER_REPO}"
                sh "docker login -u ${USERNAME} -p ${PASSWORD} ${DOCKER_REPO}"
                sh 'chmod +x ./build/deploy-service.yaml'
                sh 'chmod +x ./build/ingress.yaml'
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
        stage("${region}") {
            withCredentials([azureServicePrincipal('sp-ipim-ip-aks')]) {
                script {
                    ACRLOGINSERVER = sh(returnStdout: true, script: 'az acr show --resource-group ${AZ_RG_NAME} --name ${AZ_ACR_NAME} --query "loginServer" --output tsv').trim()
                    sh 'chmod +x ./build/deploy-service.yaml'
                    sh 'chmod +x ./build/ingress.yaml'
                    sh """
                cd build
                export CONFIGMAP=configmap-${region}-${env}
                export TARGET_HOST=azure
                export DOCKER_VERSION=${DOCKER_VERSION}
                cp \"configmap-${region}-${env}.yaml\" \"configmap-${region}-${env}-azure.yaml\"
                cp \"deploy-service.yaml\" \"deploy-service-azure.yaml\"
                cp \"ingress.yaml\" \"ingress-azure.yaml\"
                sed -i -e \"s|IMAGE_NAME_VAR|${ACRLOGINSERVER}/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}|g\" deploy-service-azure.yaml
                sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${AZ_INTERNAL_SVC_HOSTNAME}|g\" ingress-azure.yaml
                . ./deploy.sh
            """
                }
            }
        }
    }
}