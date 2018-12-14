//#!/usr/bin/env groovy
class cloudEnvironmentProps{
    def APIARY_IO_TOKEN                     = "890e555a1d3107539c130f23b9494155"

    def PROD_WESTEUROPE_AZRGNAME            = "ipimip-prod-westEurope-rg"
    def PROD_WESTEUROPE_AZACRNAME           = "acrweprod01"

    def AZURE_SVC_HOSTNAME                  = "<ENV>-az-svc.<REGION>.cloudapp.azure.com"
    def GIT_SVC_ACOUNT_EMAIL                = "l-apimgt-u-itsehbg@ikea.com"
    def GIT_SVC_ACCOUNT_USER                = "l-apimgt-u-itsehbg"
    def NPM_NEXUS_REPOSITORY_URL            = "https://nexus.hip.red.cdtapps.com/repository/npm-internal/"

    def SERVICE_GATEWAY_DNS                 = "http://dev-az-svc.westeurope.cloudapp.azure.com"

    def getApiaryIoToken(){
        return APIARY_IO_TOKEN
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
    def getGitSvcAccountEmail(){
        return GIT_SVC_ACOUNT_EMAIL
    }
    def getGitSvcAccountUser(){
        return GIT_SVC_ACCOUNT_USER
    }
    def getNpmNexusRepositoryUrl(){
        return NPM_NEXUS_REPOSITORY_URL
    }
    def getServiceGatewayDns(){
        return SERVICE_GATEWAY_DNS
    }
}