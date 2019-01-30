//#!/usr/bin/env groovy
class cloudEnvironmentProps{
    def APIARY_IO_TOKEN                     = "890e555a1d3107539c130f23b9494155"
    def APIARY_DREDD_TOKEN                  = "ce16ad7641d98a84d231ebb0b1a14292"

    def PROD_WESTEUROPE_AZRGNAME            = "ipimip-prod-westEurope-rg"
    def PROD_WESTEUROPE_AZACRNAME           = "acrweprody3qy3j"
    //    def AWS_CONTAINER_REPOSITORY_URL        = "603698310563.dkr.ecr.eu-west-1.amazonaws.com"
    def AWS_CONTAINER_REPOSITORY_URL        = "318063795105.dkr.ecr.eu-west-1.amazonaws.com"

    def AZURE_SVC_HOSTNAME                  = "<ENV>.<REGION>.svc.hip.red.cdtapps.com"
    def AWS_SVC_HOSTNAME                    = "<ENV>.<REGION>.svc.hip.red.cdtapps.com"
    def GIT_SVC_ACOUNT_EMAIL                = "l-apimgt-u-itsehbg@ikea.com"
    def GIT_SVC_ACCOUNT_USER                = "l-apimgt-u-itsehbg"
    def NPM_NEXUS_REPOSITORY_URL            = "https://nexus.hip.red.cdtapps.com/repository/npm-internal/"
    def DOCKER_IMAGE_ORG                    = "apimgt"

    def AZURE_PROD_SUBSCRIPTION_ID          = "4c58a8b3-26bd-4206-a3ca-6d1fac5d0ed5"
    def AZURE_LOWER_ENV_SUBSCRIPTION_ID     = "6795aaca-7ddd-4af7-ae6d-a984bf8d7744"

    def getApiaryIoToken(){
        return APIARY_IO_TOKEN
    }
    def getApiaryDreddToken(){
        return APIARY_DREDD_TOKEN
    }
    def getProdWesteuropeAzRgName(){
        return PROD_WESTEUROPE_AZRGNAME
    }
    def getProdWesteuropeAzAcrName(){
        return PROD_WESTEUROPE_AZACRNAME
    }
    def getAzureSvcHostname(){
        return AZURE_SVC_HOSTNAME
    }
    def getAwsSvcHostname(){
        return AWS_SVC_HOSTNAME
    }
    def getGitSvcAccountEmail(){
        return GIT_SVC_ACOUNT_EMAIL
    }
    def getGitSvcAccountUser(){
        return GIT_SVC_ACCOUNT_USER
    }
    def getNpmNexusRepositoryUrl(){
        return NPM_NEXUS_REPOSITORY_URL
    }
    def getDockerImageOrg(){
        return DOCKER_IMAGE_ORG
    }
    def getAzureProdSubscriptionId(){
        return AZURE_PROD_SUBSCRIPTION_ID
    }
    def getAzureLowerEnvSubscriptionId(){
        return AZURE_LOWER_ENV_SUBSCRIPTION_ID
    }
    def getAwsContainerRepositoryUrl(){
        return AWS_CONTAINER_REPOSITORY_URL
    }
}