def call(Map pipelineParams) {

    pipeline {
        agent any

        environment {
            BRANCH_NAME_FULL         = env.BRANCH_NAME.replace('', '')
            IMAGE_NAME               = readMavenPom().getArtifactId()
            VERSION_FROM_POM         = readMavenPom().getVersion()
            DEV_SNAPSHOT_VERSION     = "1.0.${BUILD_NUMBER}-SNAPSHOT"
            RELEASE_NUMBER           = env.BRANCH_NAME.replace('release/', '')
            RELEASE_VERSION          = "${RELEASE_NUMBER}.RELEASE"
            PROD_RELEASE_NUMBER      = readMavenPom().getVersion().replace('.RELEASE', '')

            GIT_URL_MODIFIED         = env.GIT_URL.replace('https://', 'git@').replace('com/', 'com:')

            JAVA_HOME                = "/usr/lib/jvm/java-10-oracle"
            JAVA_HOME8               = "/usr/lib/jvm/java-8-oracle"

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
            DOCKER_IMAGE_ORG                         = cloudEnvironmentProps.getDockerImageOrg()

            DOCKER_ORG_IMAGE         = "${DOCKER_IMAGE_ORG}/${IMAGE_NAME}"
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

                            //Log into Central Container Repository (ACR)
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
                         sh 'chmod +x ./mvnw'
                         sh './mvnw -f pom.xml test'

                         // Ensure project exists:
                         // curl -u 2d43347374b1c08e2e718edce7001c638f533869: -X POST “https://staging2.sonarqube.blue.azure.cdtapps.com/api/projects/create?key=ipimip.product-service.dev&name=ipimip.product-service.dev” -d ” ”  
                        // sh ''' export JAVA_HOME=$JAVA_HOME8
                         //./mvnw -f pom.xml sonar:sonar -Dsonar.login=2d43347374b1c08e2e718edce7001c638f533869 -Dsonar.projectKey=ipimip.${IMAGE_NAME}.Dev'''

                         //2d43347374b1c08e2e718edce7001c638f533869 = staging2 6.7.4

                         //aws instance 7.2
//                         sh './mvnw -f pom.xml sonar:sonar -Dsonar.login=$USERNAME -Dsonar.password=$PASSWORD'

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
//                        expression { DEPLOY_TO_AWS == 'true' }
                        expression { AWS_DEV_REGION != null }
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
                    sh "az account set -s 6795aaca-7ddd-4af7-ae6d-a984bf8d7744"
                    executeDeploy(AZURE_DEV_REGION_MAP)
                }
            }

            stage('Dredd Test') {
                when {
                    allOf {
                        branch "develop*";
                        expression { IS_API_APPLICATION == 'true' }
                    }
                }
                steps {
                    runDreddTest()
                }
            }

            stage ('TEST Deploy - AWS') {
                when {
                    allOf {
                        branch "release/*";
                        expression { DEPLOY_TO_AWS == 'true' }
                        expression { AWS_TEST_REGION == 'eu-west-1'}
                    }
                }
                steps {
                    sh "az account set -s 6795aaca-7ddd-4af7-ae6d-a984bf8d7744"
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
                    sh "az account set -s 6795aaca-7ddd-4af7-ae6d-a984bf8d7744"
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
                            script {
                                //Get variables from project deployment.properties
                                functionalTestProperties = readProperties file: './build/api-functional-testing/functional-test.properties'

                                //Collect AWS Deployment variables
                                API_FORTRESS_TEST_ID = functionalTestProperties['API_FORTRESS_TEST_ID']
                            }
                            sh "python ./build/api-functional-testing/apif-run.py run-by-id config_key -c ./build/api-functional-testing/config.yml -i ${API_FORTRESS_TEST_ID} -e \"apif_env:dev-environment\" -o test-result.json"

                        }
                    }
                    stage('Dredd Test)') {
                        steps {
                            runDreddTest()
                        }
                    }
                    stage('Security-Test') {
                        steps {
                            sh 'echo - Todo'
                        }
                    }
                    stage('Load-Test') {
                        steps {
                            sh 'echo - Todo'
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
                    sh "az account set -s 6795aaca-7ddd-4af7-ae6d-a984bf8d7744"
                    executeDeploy(AZURE_PPE_REGION_MAP)
                }
            }

            stage('PPE Deploy - AWS') {
                when {
                    allOf {
                        changeRequest target: 'master'
                        expression { DEPLOY_TO_AWS == 'true' }
                        expression { AWS_PPE_REGION == 'eu-west-1'}
                    }
                }
                steps {
                    echo "PR created to Master Branch. PPE Deployment will be performed in this stage."
                    script {
                        DOCKER_VERSION = "${PROD_RELEASE_NUMBER}"
                    }

                    executeDeploy(AWS_PPE_REGION_MAP)
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
                    sh "az account set -s 4c58a8b3-26bd-4206-a3ca-6d1fac5d0ed5"
                    executeDeploy(AZURE_PROD_REGION_MAP)
                }
            }

            stage('PROD Deploy Release - AWS') {
                when {
                    allOf {
                        branch 'master';
                        expression {DEPLOY_TO_AWS == 'true'}
                        expression { AWS_PROD_REGION == 'eu-west-1'}
                    }
                }
                steps {
                    echo 'Merge request to Master Branch has been approved. PROD Deployment will be performed in this stage.'
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
                    sh "az account set -s 4c58a8b3-26bd-4206-a3ca-6d1fac5d0ed5"
                    executeDeploy(AZURE_PROD_REGION_MAP)
                }
            }

            stage('PROD Deploy HotFix - AWS') {
                when {
                    allOf {
                        branch "hotfix/*"
                        expression {DEPLOY_TO_AWS == 'true'}
                        expression { AWS_PROD_REGION == 'eu-west-1'}
                    }
                }
                steps {
                    echo 'HotFix change has been implemented. PROD Deployment will be performed in this stage.'
                    executeDeploy(AWS_PROD_REGION_MAP)
                }
            }

            stage('GIT Commit Changes') {
                when {
                    anyOf {
                        branch 'develop*';
                        branch "release/*"
                        branch "hotfix/*"
                        branch "master"
                        changeRequest target: 'master'
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

            stage('GIT PR from Release to Dev') {
                when {
                    changeRequest target: 'master'
                }
                steps {
                    echo "Creating a PR from Release Branch to Develop Branch"
                    script {
                        try {
                            sh 'hub pull-request -b develop -m "PR Created from Release Branch to Develop Branch."'
                        } catch (err) {
                            echo 'Develop Branch does not exist? Trying Development Branch'
                            sh 'hub pull-request -b development -m "PR Created from Release Branch to Develop Branch."'
                        }
                    }
                }
            }

            stage('GIT Create Tag from Release') {
                when {
                    allOf {
                        branch 'master';
                    }
                }
                steps {
                    withCredentials([sshUserPrivateKey(credentialsId: 'l-apimgt-u-itsehbgATikea.com', keyFileVariable: 'SSH_KEY')]) {
                        withEnv(["GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no -o User=${GIT_SVC_ACCOUNT_USER_PROP} -i ${SSH_KEY}"]) {
                            echo 'Merge request to Master Branch has been approved. PROD Deployment will be performed in this stage.'
                            sh "git tag ${PROD_RELEASE_NUMBER}"
                            sh 'git push origin --tags'
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
                sh 'chmod +x ./build/istio/*.yaml'
                sh """
                        mkdir -p ~/.aws
                        cp ./build/aws/credentials ~/.aws/credentials
                        cp ./build/aws/config ~/.aws/config
                        export AWS_PROFILE=eks@ikea-${env}

                        docker login -u AWS -p eyJwYXlsb2FkIjoieE5RY0NZYzBxbXc5YkRoUGJHWHFvSFY1K1lWekJPRXhVazB6TWlVTE5xUVhkYzh5Z0w1K042c2FjRzY4SzF0cWdLMjNlaUpZUWxaUEQrbGJsUkpWcFNqQ01MUWxZRnJudVZGQWpva2VMZ1FLdlpsMnVZV003cTV1NW5KVjl5VUlGcmVKZ1hVSThueEVxQjFqVExXanJQUWxIb0pQTGcrazhHZmEraXhLRHRiMzh3d25WQ2V2eHdwbnZFR2ltU2lMbVM0akVqb25haDZNSFFDSE5wR2lZRXg5ZGdMcHl2dVA2M1FPemhrWG55LzY1dUFVZGV5UFRBY3FsbXBZQjNuZ2J4UW5OVnVZUHlmd2E1a0hkVXNFdEVuRzdlemFyOTlLVGJNQmpzTzhUVk1aMmptZ2Q2NkQzV3hSemRSUFd0Mjlnc1dtWmp5SHlyRmRveVdyVWhMM1JhMXJ3OW1iaUJuUUN4MU5QQ0hPR3poNENFMkZIbFFCN0kwdEhFNldOQmdacHNIamJYcFMvT2pEL3dOZUgvcEtMeXUwQWl5RGtLWVdYK1p5YU1GRXdUeHRueGVHT0tIcisxTTNVWXVVVnNVUWJPbnZ6VjJqZFZGOTJpY1hKRWRXTmFTTEkvTFVFdmkybFVBejVWUy84NFc3YTNGUmZoc1FrYzQ5QWJpS25TMkVTMFR0blgxcmtPd2tINVVzVWV6Sk5IVmZuZHIzSTJlQkN2Z1N6OHRvdTJzSUQ1YWtUb1FnbVZOSngxNlZ1MGJGSUJOSHhIUEdzcTV3U2I1bzlTMllIL25qYjcwTVMvWHJMd0poNHB1Wll3RkltdWNPVXBIVHNEV25sOWNMdHBTUU1Qa3pXM3QzTTFBU0dWM2RTcndZbGRDSHRZY0U5SzBPQTd0TTVnMEF1TVpLL1l1ZXFyQXRacjRPYkV3OHZEdVJzZDg4bkk3Wmk2RW9yd2Uxa0puNzRuVS9qcHc4SFhXRzVUeUdONyt2MldXT25WTVA2UWhDa1JQMFRKNE5tU2pQWUgyMGdLdlFETnJGeG5ZU3N3bEQxZ0ZrN1N2VVp1R0FKZGM3VHdlZlVEdXI5aEh6QUkyTS9XZG45YjBiWHBFOThJSEpQMERuMW14S1l1RC9ERThhTitON1FVSnFCTGF5dG5kQ3pmenRjUlJsRDFtOW04bWhCbVhzWnVNZWp6QThEU0xFL3JIWVYva1gvYmVuTGtvMDNNMXB2elFycDlkczVuTGFydlJSMWkzV0xmT0o2Q2Z6bC9ZdHc1NTZsenRNOUNVM3I4K1IyNEVJNmZzS3ljY04yd0VsaEhybzRpZTJwbjNWQ2JxV1Z5c29FdkVlYW9HZWwxWnYxeUpmL3ZMdjhYcEsrbURZVVdxTnBkWlBlVk1hUTlRbDlUR2hwZTQyQ0hid1F2Rk1DdkI5SmZIN0taVkhGeGVBeVZDS2tkR2oyY1NERWhGbi82RjYzbXN5Wm4zQ2FKUmE3b1BtNEFtZTZSQTZBcGx2cFpLVG1rVlpKODlvK1UzZitJRXlkOHdDelljTHk0QXRMSlNSczNBdDNzV21BcVlLZEpEUmo3OGJFY21GRlIwdFZXTVAvbExVdU9FdnRlZjNxbmo5L1IrVkNPSXJHSzNiWkxEWDd6bjRpYlZvWlZiSzBDeitkRS9ucmx6ZUhoMmtOZlFNeFRHbFpkanpxSWxmenRWS1hQNDVMWVY4Mjc4UXFiMkg1SGFKTWwraWJVNGFmUVcvRjluVnVjaGV5aklpUURhWUR5cGEyZGZuUzZNeXNMUnJvZz09IiwiZGF0YWtleSI6IkFRRUJBSGgrZFMrQmxOdTBOeG5Yd293YklMczExNXlqZCtMTkFaaEJMWnN1bk94azNBQUFBSDR3ZkFZSktvWklodmNOQVFjR29HOHdiUUlCQURCb0Jna3Foa2lHOXcwQkJ3RXdIZ1lKWUlaSUFXVURCQUV1TUJFRURBQ3hQQ1N6UHpSZDZWZlNtQUlCRUlBN0FZOGNTcmx2a2VaTkYwWjhLS0F3emdjK2d3UjRyeHBxV1dpa1pFYjdOZ0tDOXNOTml5d3JxL0ZqeWlVQzF3VEJYcEV0QUdxa2RWbmJzdEk9IiwidmVyc2lvbiI6IjIiLCJ0eXBlIjoiREFUQV9LRVkiLCJleHBpcmF0aW9uIjoxNTQ4NjU2ODE0fQ== https://603698310563.dkr.ecr.eu-west-1.amazonaws.com
                        
                        docker tag acrweprod01.azurecr.io/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION} 603698310563.dkr.ecr.eu-west-1.amazonaws.com/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}
                        docker push 603698310563.dkr.ecr.eu-west-1.amazonaws.com/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}

                        aws eks update-kubeconfig --kubeconfig ./build/aws/awskubeconfig --name cluster1
                        chmod +x ./build/aws/aws-iam-authenticator
                        
                        ls -ltr ./build/aws

                        sed -i -e \"s|aws-iam-authenticator|./aws-iam-authenticator|g\" ./build/aws/awskubeconfig
                        kubectl --kubeconfig ./build/aws/awskubeconfig get pods

                        cd build/istio
                        cp \"configmap-aws-${region}-${env}.yaml\" \"configmap-aws-${region}-${env}-aws.yaml\"
                        cp \"deploy-service.yaml\" \"deploy-service-aws.yaml\"
                        cp \"virtual-service.yaml\" \"virtual-service-aws.yaml\"
                        cp \"destination-rule.yaml\" \"destination-rule-aws.yaml\"
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" configmap-aws-${region}-${env}-aws.yaml
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f configmap-aws-${region}-${env}-aws.yaml

                        sed -i -e \"s|IMAGE_NAME_VAR|603698310563.dkr.ecr.eu-west-1.amazonaws.com/${DOCKER_ORG_IMAGE}:${DOCKER_VERSION}|g\" deploy-service-aws.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}|g\" deploy-service-aws.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" deploy-service-aws.yaml
                        sed -i -e \"s|CONFIGMAP_NAME_VAR|${IMAGE_NAME}-configmap|g\" deploy-service-aws.yaml
                        sed -i -e \"s|VERSION_VAR|${DOCKER_VERSION}|g\" deploy-service-aws.yaml
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f deploy-service-aws.yaml
                        
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|INTERNAL_SVC_HOSTNAME_VAR|${AWS_ENV_REGION_SVC_HOSTNAME}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|SVC_PATH_VAR|${URI_ROOT_PATH}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|ENV_VAR|${env}|g\" virtual-service-aws.yaml
                        sed -i -e \"s|REGION_VAR|${region}|g\" virtual-service-aws.yaml
                        kubectl --kubeconfig ../aws/awskubeconfig apply -f virtual-service-aws.yaml
                        
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}|g\" destination-rule-aws.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" destination-rule-aws.yaml 
                        sed -i -e \"s|LABEL_APP_VAR|${IMAGE_NAME}|g\" destination-rule-aws.yaml                       
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
                        
                        sed -i -e \"s|SERVICE_NAME_VAR|${IMAGE_NAME}|g\" destination-rule-azure.yaml
                        sed -i -e \"s|KUBERNETES_NAMESPACE_VAR|${KUBERNETES_NAMESPACE}|g\" destination-rule-azure.yaml 
                        sed -i -e \"s|LABEL_APP_VAR|${IMAGE_NAME}|g\" destination-rule-azure.yaml                       
                        kubectl apply -f destination-rule-azure.yaml
                       """
                }
            }
        }
    }
}

def logIntoAzure(){
    //Log into ACR/ECR etc
    sh "az login --service-principal -u ${AZURE_CLIENT_ID} -p ${AZURE_CLIENT_SECRET} -t ${AZURE_TENANT_ID}"
//    sh "az account set -s ${AZURE_SUBSCRIPTION_ID}"
    //Use Prod Subscription ID
    sh "az account set -s 4c58a8b3-26bd-4206-a3ca-6d1fac5d0ed5"
    sh "az acr login --name ${PROD_WESTEUROPE_AZACRNAME_PROP}"
    ACRLOGINSERVER = sh(returnStdout: true, script: "az acr show --resource-group ${PROD_WESTEUROPE_AZRGNAME_PROP} --name ${PROD_WESTEUROPE_AZACRNAME_PROP} --query \"loginServer\" --output tsv").trim()
}

void executeDeploy(Map inboundMap) {
    def mapValues = inboundMap.values();
    for (customStage in mapValues) {
        script customStage
    }
}

def runDreddTest(){
    //Install Dependencies
    sh 'node -v'
    sh 'npm -v'
    sh 'npm install'
    sh 'npm -g install dredd@stable'
    //Firstly fetch the latest API Blueprint Definition.
    script {
        sh """
           cd ./build/api-contract-testing
           export APIARY_API_KEY=${APIARY_IO_TOKEN_PROP}
           apiary fetch --api-name ${APIARY_PROJECT_NAME} --output ${APIARY_PROJECT_NAME}.apib
           """
        sh "git add ./build/api-contract-testing/${APIARY_PROJECT_NAME}.apib"
    }
    script {
        AZ_ENV_REGION_SVC_HOSTNAME = "${AZURE_SVC_HOSTNAME_PROP}".replace('<ENV>', "dev").replace('<REGION>', "westeurope")
        //Make copy of dredd-template (to stop git automatically checking in existing modified file
        sh 'cp ./build/api-contract-testing/dredd-template.yml ./build/api-contract-testing/dredd.yml'
        //Replace variables in Dredd file
        sh """
           cd build/api-contract-testing
           sed -i -e \"s|APIARY_PROJECT_VAR|${APIARY_PROJECT_NAME}.apib|g\" dredd.yml
           sed -i -e \"s|SERVICE_GATEWAY_DNS_VAR|http://${AZ_ENV_REGION_SVC_HOSTNAME}|g\" dredd.yml
           """
    }
    //Run Dredd Test against APIB Definition and running service.
    script {
        try {
            sh """
               export APIARY_API_KEY=${APIARY_IO_DREDD_PROP}
               export APIARY_API_NAME=${APIARY_PROJECT_NAME}
               dredd --config ./build/api-contract-testing/dredd.yml
               """
        } catch (err) {
            //TODO
            echo 'Dredd Test failed. Continuing with pipeline'
        }
    }
}